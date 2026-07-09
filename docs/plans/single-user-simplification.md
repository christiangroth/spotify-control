# Single-User Simplification Plan

This document is the implementation plan for [ADR-0008](../adr/0008-single-user-architecture.md):
converting spotify-control from an allow-listed multi-user design to a strict single-user
application. It only adds this plan; implementation happens in separate, smaller follow-up PRs,
one phase (or sub-phase) at a time, each with a green build per this repository's
[CI requirements](../../CLAUDE.md).

The system currently threads `UserId` through almost every module. Because of that, the migration
is organized module-by-module, in dependency order (`domain-api` → `domain-impl` → adapters →
schema → cleanup), so each phase can be built and tested independently before the next begins.

---

## Phase 1: Login and allow-list removal — done

**Goal:** Login no longer checks an allow-list; any Spotify account can log in, and the concept of
"not allowed" disappears.

* `domain-impl/src/main/kotlin/de/chrgroth/spotify/control/domain/user/LoginService.kt` –
  remove the injected `app.allowed-spotify-user-ids` property, the `Set<UserId>` allow-list, and
  `isAllowed(userId)`. `handleCallback` no longer branches on allow-list membership or raises
  `AuthError.USER_NOT_ALLOWED`.
* `domain-impl/src/main/resources/application.properties` – remove
  `app.allowed-spotify-user-ids` (prod/dev/test values).
* `adapter-in-web/.../OAuthResource.kt` – remove the "user not allowed" error redirect path if it
  becomes unreachable.
* Any `AuthError` variant that only existed to represent "not allowed" can be removed once no
  producer/consumer remains.
* Update `docs/arc42/arc42.md` "Authentication and Access Control" section (already updated in
  this PR, see below) and remove `APP_ALLOWED_SPOTIFY_USER_IDS` from deployment configuration
  once this phase ships.

**Risk:** Low. This is an additive-to-permissive change (fewer checks, not more), and the
single deployed instance is already only ever logged in by one operator.

---

## Phase 2: Scheduler fan-out and the catalog-sync shortcut — done

**Goal:** Remove the "for each user" loops in scheduled job enqueuing, since there's only ever one
user, and remove the arbitrary-user shortcut in catalog sync (performance review Finding 1).

Per-user fan-out loops to collapse to a single call against the one stored user:

* `domain-impl/.../playback/PlaybackService.kt` (`enqueueFetchPlaybackData`, ~line 68-73)
* `domain-impl/.../playback/PlaybackAggregationService.kt` (four `users.forEach` blocks for
  daily/weekly/monthly/quarterly/yearly aggregation, ~line 55-84)
* `domain-impl/.../playlist/PlaylistService.kt` (`enqueueUpdates`, ~line 63-65)
* `domain-impl/.../user/UserProfileService.kt` (`enqueueUpdates`, ~line 27-33)

Catalog-sync shortcut to remove:

* `domain-impl/.../catalog/CatalogService.kt` (lines ~101-206) currently does
  `userRepository.findAll().firstOrNull()?.spotifyUserId` to obtain *a* token for catalog-wide
  sync operations. Once single-user is enforced this becomes `userRepository.findAll().single()`
  (or an equivalent explicit "the one user" lookup) — the same call, but now correct by
  construction instead of an arbitrary pick among many.
* `domain-impl/.../catalog/CatalogBrowserService.kt` (~line 209) — same pattern.

**Risk:** Medium. Touches the busiest scheduled jobs in the system; needs test coverage for the
"zero users yet" (not logged in) and "one user" cases. No "multiple users" case needs testing
afterward, since it becomes structurally impossible.

---

## Phase 3: Thread removal of `UserId` through ports and services — done

**Goal:** Drop `UserId` parameters from ports, services, and outbox events where they only ever
existed to distinguish between users, once Phase 2 has established that there is exactly one user.

**User profile slice — done:** `UserProfilePort.getDisplayName()` and `update()` no longer take a
`UserId`; both resolve the one stored user internally via `CurrentUserResolver`. The
`UpdateUserProfile` outbox event dropped its `userId` field (same placeholder-payload pattern as
`ResyncCatalog`).

**`SpotifyAccessTokenPort` slice — done:** `getValidAccessToken()` no longer takes a `UserId`;
`SpotifyAccessTokenAdapter` resolves the one stored user directly via `UserRepositoryPort.findAll()`
(it lives in `adapter-out-spotify`, which has no dependency on `domain-impl`'s `CurrentUserResolver`,
so it repeats the same "single stored user" lookup instead). All call sites in `PlaybackService`,
`CatalogService`, `PlaylistService`, `PlaylistCheckService`, `UserProfileService`, and
`SpotifyDebugResource` already had `userId` in scope for other calls, so only the token-fetch
argument was dropped.

**SSE / dashboard slice — done:** `DashboardPort` (`getStats`, `getPlaybackStats`,
`getPlaylistMetadata`, `getRecentlyPlayed`, `getListeningStats`) and `DashboardRefreshPort`
(`notifyUserPlaybackData`, `notifyUserPlaylistMetadata`, `notifyUserPlaylistChecks`) no longer take
a `UserId`; `DashboardService` resolves the one stored user internally via `CurrentUserResolver`
(returning `DashboardStats.EMPTY` for the guard-clause "no user yet" case), and
`DashboardSseAdapter` collapsed its per-user emitter map to a single shared emitter list, since
there is only ever one possible subscriber. `DashboardResource`, `PlaybackResource`, and
`DashboardSseResource` updated their call sites accordingly.

**Playback, playlist, and catalog slices — done:** `PlaybackPort` (`fetchPlaybackData`,
`enqueueRebuildPlaybackData`, `rebuildPlaybackData`, `appendPlaybackData`), `PlaybackEventViewerPort
.getEvents()`, `PlaylistPort` (`getPlaylists`, `getTrackCounts`, `syncPlaylists`,
`syncPlaylistData`, `updateSyncStatus`, `updatePlaylistType`, `enqueueSyncPlaylistData`),
`PlaylistCheckPort` (`getCheckDashboard`, `runFix`), and `CatalogPort.syncArtistDetails` no longer
take a `UserId`; each service resolves the one stored user internally via `CurrentUserResolver`
(with the pre-existing guard-clause pattern for the "no user yet" case). `SpotifyPlaybackPort`,
`SpotifyPlaylistPort`, and `SpotifyCatalogPort` also dropped `UserId` — `SpotifyPlaylistPort` and
`SpotifyCatalogPort` only ever used it for log messages, but `SpotifyPlaybackPort` actually stamped
it into the returned `CurrentlyPlayingItem`/`RecentlyPlayedItem` domain objects, so `PlaybackService`
now stamps the real user onto those items itself right after the adapter call, since the adapter no
longer has a `UserId` to use.

The outbox events that only carried `userId` to route to "the current user"
(`FetchPlaybackData`, `RebuildPlaybackData`, `AppendPlaybackData`, `SyncPlaylistInfo`,
`SyncPlaylistData`, `SyncArtistDetails`, `SyncArtistAlbums`, `RunPlaylistChecks`,
`AggregatePlaybackData`) dropped the field, following the same placeholder-payload pattern already
established by `UpdateUserProfile`/`ResyncCatalog`. Backward compatibility with in-flight outbox
tasks queued under the old payload format is handled directly in `fromKey`/`fromPayload`: no-arg
events ignore their payload entirely, and events with real remaining fields (e.g.
`SyncArtistDetails.artistId`) parse them with `substringBefore/-After(':')`, which transparently
strips a leading `userId:` prefix if present without needing a separate legacy code path.

Repository out-ports (`AppPlaybackRepositoryPort`, `PlaylistRepositoryPort`,
`CurrentlyPlayingRepositoryPort`, etc.) were intentionally left untouched — they still key their
MongoDB queries by `spotifyUserId` until Phase 4 migrates the schema, and internal service code
still resolves and threads `UserId` down to those calls, just no longer as a public port parameter.

---

## Phase 4: MongoDB schema and index cleanup — done

**Goal:** Simplify collections and indexes that currently exist to disambiguate between users.

| Collection | Old key | New key |
|---|---|---|
| `app_user` | `_id = spotifyUserId` | Unchanged; still the single source of truth for "who is the user," now guaranteed to have at most one document. |
| `app_playback` | `_id = "${spotifyUserId}:${playedAt.epochMilli}"` | `_id = playedAt.epochMilli` |
| `app_playback_aggregation` | `_id = "${spotifyUserId}:${type}:${periodStart}"` | `_id = "${type}:${periodStart}"` |
| `spotify_currently_playing` | `spotifyUserId` field + compound index `(spotifyUserId, trackId, observedAt)` | Field dropped; index shrunk to `(trackId, observedAt)`. |
| `spotify_recently_played` | `spotifyUserId` field + index `(spotifyUserId, playedAt)` | Field dropped; index shrunk to `(playedAt)`. |
| `spotify_recently_partial_played` | same pattern | Field dropped; index shrunk to `(playedAt)`. |
| `spotify_playlist` | `_id = "${spotifyUserId}:${playlistId}"` + index `spotify_playlist_spotifyUserId_1` | `_id = spotifyPlaylistId`; index dropped. |
| `spotify_playlist_metadata` | `_id = "${spotifyUserId}:${playlistId}"` + index | `_id = spotifyPlaylistId`; index dropped. |

`app_artist`, `app_album`, `app_track` already had no `userId` and were unaffected.

**Migration approach:** a one-time `SimplifySingleUserSchemaStarter` (per the existing pattern in
`adapter-in-starter`, see arc42 "Starters" section) strips `spotifyUserId` from existing
`spotify_currently_playing`/`spotify_recently_played`/`spotify_recently_partial_played` documents,
and rewrites the composite `_id`s (insert-then-delete, since MongoDB can't change an existing
`_id` in place) for `app_playback`, `app_playback_aggregation`, `spotify_playlist`, and
`spotify_playlist_metadata`.

Beyond the schema/index/migration work, this phase also went further than originally scoped and
removed the now-dead `UserId`/`spotifyUserId` parameters and fields from the repository out-ports
themselves (`AppPlaybackRepositoryPort`, `PlaybackAggregationRepositoryPort`,
`CurrentlyPlayingRepositoryPort`, `RecentlyPlayedRepositoryPort`,
`RecentlyPartialPlayedRepositoryPort`, `PlaylistRepositoryPort`, `PlaybackEventViewerRepositoryPort`)
and from the in-memory domain models (`AppPlaybackItem`, `CurrentlyPlayingItem`,
`RecentlyPlayedItem`, `RecentlyPartialPlayedItem`, `PlaybackAggregation`), since those fields were
never read once Phase 3 moved user resolution to `CurrentUserResolver` — leaving them in place
would just have been dead weight passed around for no reason. All call sites in `domain-impl` and
`adapter-in-web`, and the `PlaylistCheckRunner` interface (which took a `UserId` purely to forward
into playlist lookups), were updated accordingly.

**Risk:** Medium. Required careful index rebuild sequencing (`MongoIndexInitializer.kt`) and ran
only after Phase 3 confirmed no code still reads/writes the dropped fields.

---

## Phase 5: Configuration, tests, and deployment cleanup

**Goal:** Remove now-dead configuration, test fixtures, and documentation references.

* Remove `APP_ALLOWED_SPOTIFY_USER_IDS` from `docs/arc42/arc42.md` Configuration section,
  deployment `.env` templates, and GitHub Actions secrets once Phase 1 has shipped.
* Test fixtures that currently create multiple `test-user-a` / `test-user-b` accounts (see
  `domain-impl`'s test profile allow-list default) should be reduced to a single test user.
  Any test asserting "user B cannot see user A's data" becomes obsolete and should be removed
  rather than adapted.
* Review `docs/reviews/2026-07-08-performance-review.md` Findings 1, 2, and the "what breaks
  first" multi-user scaling section — once this migration completes, note in that review (or a
  follow-up note) that those risks are resolved by design.

**Risk:** Low. Cleanup only, no behavioral change.

---

## Summary of risks across phases

| Phase | Risk | Mitigation |
|---|---|---|
| 1 – Login/allow-list removal | Low | Permissive change; single real operator already unaffected. |
| 2 – Scheduler fan-out & catalog-sync shortcut | Medium | Done — "zero users" and "one user" cases covered by tests. |
| 3 – `UserId` threading removal | High | Done — split per bounded context (user-profile, `SpotifyAccessTokenPort`, SSE/dashboard, then playback/playlist/catalog); in-flight outbox payloads with stale `userId` fields are tolerated by `fromKey`/`fromPayload`. |
| 4 – MongoDB schema/index cleanup | Medium | Done — one-time `SimplifySingleUserSchemaStarter`; sequenced after Phase 3; also removed the now-dead `UserId` parameters from repository ports and domain models. |
| 5 – Config/tests/deploy cleanup | Low | Cleanup only. |

## Out of scope

* Any change to the Spotify OAuth flow itself beyond removing the allow-list check — the
  Authorization Code Flow, token encryption, and session cookie mechanism are unaffected.
* `app_artist`, `app_album`, `app_track` — already user-agnostic shared catalog data.
