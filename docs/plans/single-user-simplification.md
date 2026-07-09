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

## Phase 3: Thread removal of `UserId` through ports and services — in progress

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

Remaining slices (playback, playlist, catalog) are still open. These are substantially larger:
unlike the slices above, the `UserId` in `PlaybackPort`, `PlaylistPort`, `PlaylistCheckPort`,
`PlaybackEventViewerPort`, `CatalogPort`, `SpotifyPlaybackPort`, `SpotifyCatalogPort`, and
`SpotifyPlaylistPort` also flows into repository out-ports (`AppPlaybackRepositoryPort`,
`PlaylistRepositoryPort`, `CurrentlyPlayingRepositoryPort`, etc.) that still key their MongoDB
queries by `spotifyUserId` until Phase 4 migrates the schema, and into every `adapter-in-web`
resource. Per the risk note below, do these as separate, bounded-context PRs rather than one large
change.

* `domain-api/.../port/out/*` – ports whose only use of `UserId` is to select "which user" (not
  domain data itself) can drop the parameter: `PlaybackPort`, `PlaybackAggregationPort`,
  `PlaylistPort`, `SpotifyPlaybackPort`, `SpotifyCatalogPort`, `SpotifyPlaylistPort`.
  Note that `CatalogPort` needs care — some of the `UserId` usages there are about *which token to
  use for a Spotify call*, which still needs to resolve to "the one user," not "no user."
* `domain-api/.../domain/outbox/DomainOutboxEvent.kt` – outbox event payloads that carry `userId`
  purely to route to "the current user" (`FetchPlaybackData`, `RebuildPlaybackData`,
  `AppendPlaybackData`, `SyncArtistAlbums`, etc.) can drop the field. Existing in-flight outbox
  tasks in MongoDB will already contain the old payload shape — deserialization must tolerate
  (ignore) a stray `userId` field during the transition, or old tasks must be drained before
  deploying this phase.
* `adapter-in-web/.../*Resource.kt` – all resources currently resolve "current user" via
  `UserId(securityIdentity.principal.name)` (`PlaybackResource`, `StatsResource`,
  `PlaylistsResource`, `PlaylistSettingsResource`, `PlaybackSettingsResource`,
  `PlaylistChecksResource`, `PlaybackEventViewerResource`, `HealthSseResource`). This resolution
  can stay as-is (it still identifies the logged-in session), but downstream calls no longer need
  to pass it further than the point where a Spotify token must be selected.

**Risk:** High — this phase touches the largest surface area (most of `domain-api` and
`domain-impl`, and all of `adapter-in-web`). Recommend splitting further into one PR per bounded
context (playback, playlist, catalog, user profile) rather than one large PR.

---

## Phase 4: MongoDB schema and index cleanup

**Goal:** Simplify collections and indexes that currently exist to disambiguate between users.

| Collection | Current key | Proposed change |
|---|---|---|
| `app_user` | `_id = spotifyUserId` | Keep as-is; still the single source of truth for "who is the user," now guaranteed to have at most one document. |
| `app_playback` | `_id = "${spotifyUserId}:${playedAt.epochMilli}"` | Drop `spotifyUserId` from the key; `_id = playedAt.epochMilli` (or keep the compound key — see risk note below). |
| `app_playback_aggregation` | `_id = "${spotifyUserId}:${type}:${periodStart}"` | Drop `spotifyUserId` from the key: `_id = "${type}:${periodStart}"`. |
| `spotify_currently_playing` | `spotifyUserId` field + compound index `(spotifyUserId, trackId, observedAt)` | Drop `spotifyUserId` field and shrink index to `(trackId, observedAt)`. |
| `spotify_recently_played` | `spotifyUserId` field + index `(spotifyUserId, playedAt)` | Drop field, shrink index to `(playedAt)`. |
| `spotify_recently_partial_played` | same pattern | Drop field, shrink index to `(playedAt)`. |
| `spotify_playlist` | `spotifyUserId` field + index `spotify_playlist_spotifyUserId_1` | Drop field and index. |
| `spotify_playlist_metadata` | `spotifyUserId` field + index | Drop field and index. |

`app_artist`, `app_album`, `app_track` already have no `userId` and are unaffected.

**Migration approach:** use a one-time `Starter` (per the existing pattern in
`adapter-in-starter`, see arc42 "Starters" section) to strip `spotifyUserId` from existing
documents and rebuild indexes, rather than a manual migration script — this matches how the
project already handles one-time schema changes. Because there has only ever been one real user,
this is a low-risk, single-pass rewrite, not a multi-tenant data migration.

**Risk:** Medium. Requires careful index rebuild sequencing (`MongoIndexInitializer.kt`) to avoid
downtime, and must run after Phase 3 so no code still reads/writes the dropped field.

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
| 3 – `UserId` threading removal | High | Split per bounded context; handle in-flight outbox payloads carrying stale `userId` fields during rollout. User-profile, `SpotifyAccessTokenPort`, and SSE/dashboard slices done. |
| 4 – MongoDB schema/index cleanup | Medium | Use a one-time `Starter`; sequence after Phase 3; rebuild indexes without downtime. |
| 5 – Config/tests/deploy cleanup | Low | Cleanup only. |

## Out of scope

* Any change to the Spotify OAuth flow itself beyond removing the allow-list check — the
  Authorization Code Flow, token encryption, and session cookie mechanism are unaffected.
* `app_artist`, `app_album`, `app_track` — already user-agnostic shared catalog data.
