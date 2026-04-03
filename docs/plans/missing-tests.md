# Missing Tests

After the test cleanup that applied the "Test Your Boundaries" principle strictly, all tests except the
Quarkus integration tests in `application-quarkus` were deleted. This document tracks the domain logic
and adapter-local logic that currently has no test coverage, ranked by business impact.

The existing tests in `application-quarkus` cover the following boundaries:

* **L2 – Outbound adapters:** MongoDB repositories (album, artist, track, playlist, recently played,
  playback, user) and Spotify API adapters (auth, currently playing, recently played, playlist tracks)
* **L3 – Inbound adapters:** All HTTP pages (dashboard, health, config, docs, login, runtime config,
  settings) and the full OAuth flow
* **L4 – App wiring:** Health endpoints, metrics endpoint
* **Contract tests:** All `DomainOutboxEvent` subtypes (deduplication keys, payload round-trips,
  handler methods)

---

## Domain Logic (was: L1 tests in `domain-impl`)

These cover business rules in domain services. They would be written as `@QuarkusTest` tests in
`application-quarkus`, calling the domain service through its inbound port interface with
`@InjectMock` on all outbound ports.

### LoginService – OAuth callback and allowlist

**Class:** `domain-impl/.../user/LoginService`  
**Port:** `LoginServicePort.handleCallback(code: String)` / `isAllowed(userId)`

* `handleCallback` – allowed user: exchange code → fetch profile → encrypt tokens → upsert user →
  return `UserId`
* `handleCallback` – not-allowed user: exchange code → fetch profile → profile not in allowlist →
  return `AuthError.USER_NOT_ALLOWED`, user must NOT be upserted
* `handleCallback` – `exchangeCode` fails: propagate `AuthError.TOKEN_EXCHANGE_FAILED`, no further
  calls
* `handleCallback` – `getUserProfile` fails: propagate `AuthError.PROFILE_FETCH_FAILED`
* `handleCallback` – `upsert` throws unexpected exception: return `AuthError.UNEXPECTED`
* `isAllowed` – returns `true` for user in configured allowlist
* `isAllowed` – returns `false` for user not in allowlist

### PlaylistService – sync and track management

**Class:** `domain-impl/.../playlist/PlaylistService`  
**Port:** `PlaylistPort`

* `syncPlaylists` enqueues `SyncPlaylistInfo` outbox event per user
* `handleSyncPlaylistInfo` processes playlist metadata and enqueues `SyncPlaylistData` for each
  outdated playlist
* `handleSyncPlaylistData` fetches tracks and updates repository; enqueues follow-up paginated event
  when Spotify returns a next-page URL
* `handleSyncPlaylistData` skips playlist when snapshot hasn't changed
* `addTrack` resolves duplicate in target playlist and removes it before adding to source playlist
* `addTrack` returns error when track not found in catalog

### PlaylistCheckService – integrity check orchestration

**Class:** `domain-impl/.../playlist/PlaylistCheckService`  
**Port:** `PlaylistCheckPort.runChecks(userId, playlistId)`

* All applicable runners are executed in parallel for a given playlist
* Non-applicable runners are skipped (based on `isApplicable`)
* Check results are persisted via `AppPlaylistCheckRepositoryPort`
* Failures trigger a notification via `PlaylistCheckNotificationPort`

### DuplicateTrackIdsCheckRunner – duplicate track detection

**Class:** `domain-impl/.../playlist/check/DuplicateTrackIdsCheckRunner`

* Returns `succeeded = true` when playlist has no duplicate track IDs
* Returns `succeeded = false` with formatted violations (`"Artist – Title"`) when duplicates exist
* Fetches track metadata from `AppTrackRepositoryPort` only when duplicates are found (no
  unnecessary catalog queries)
* Throws `IllegalArgumentException` when a duplicate track ID is not found in the catalog

### YearSongsInAllCheckRunner – year-playlist completeness

**Class:** `domain-impl/.../playlist/check/YearSongsInAllCheckRunner`

* Check is only applicable when `PlaylistInfo.type == YEAR`
* Returns `succeeded = true` when all year-playlist tracks are present in the "ALL" playlist
* Returns `succeeded = false` with formatted violations for tracks missing in "ALL"
* Returns `succeeded = true` (no violations) when the "ALL" playlist is not found in the repository

### CatalogService – catalog sync and artist/album enrichment

**Class:** `domain-impl/.../catalog/CatalogService`  
**Port:** `CatalogPort`

* `handleSyncArtistDetails` fetches artist details from Spotify and upserts into repository;
  enqueues `SyncAlbumDetails` for each new album
* `handleSyncAlbumDetails` fetches album details from Spotify and upserts tracks into repository
* `resyncArtist` resets artist sync status and re-enqueues `SyncArtistDetails`
* Artists already synced are not re-enqueued (deduplication)

### PlaybackEnrichmentService – enrichment pipeline

**Class:** `domain-impl/.../catalog/PlaybackEnrichmentService`

* `handleAppendPlaybackData` enriches raw playback events with artist and track metadata from catalog
* `handleRebuildPlaybackData` rebuilds the full enriched playback dataset from scratch
* Missing catalog entries (tracks / artists not yet synced) are handled gracefully without aborting

### SyncController – catalog sync orchestration

**Class:** `domain-impl/.../catalog/SyncController`

* `syncCatalog` triggers artist sync for all artists that have not been fully synced yet
* Enqueues `SyncArtistDetails` outbox event for each unsynced artist
* Does not re-enqueue artists that are already in a synced or processing state

### RecentlyPlayedService – recently played history

**Class:** `domain-impl/.../playback/RecentlyPlayedService`

* `handleFetchRecentlyPlayed` fetches recent plays from Spotify and persists new entries only
* Deduplicates entries: does not insert tracks already present in recently played history
* Partial plays (below a threshold) are tracked separately and not counted as a full listen
* `handleFetchCurrentlyPlaying` updates currently playing state and triggers `PlaybackDetectedObserver`

### SyncArtistPlaybackFromPlaylists – cross-domain enrichment

**Class:** `domain-impl/.../playback/SyncArtistPlaybackFromPlaylists`

* Assigns playlist-sourced playback entries to artists in the catalog
* Skips entries where the artist is not found in the artist repository

### DashboardService – stats aggregation

**Class:** `domain-impl/.../infra/DashboardService`  
**Port:** `DashboardPort.getDashboardStats(userId)`

* Aggregates total playback event count, last-30-days count, and histogram buckets
* Computes top tracks and top artists from enriched playback data
* Calculates total listened minutes
* Returns correct `DashboardStats` shape even when repositories return empty results

---

## Adapter-Local Logic (was: L5 tests in individual adapter modules)

These cover non-trivial pure logic in adapters. They would be written as `@QuarkusTest` tests in
`application-quarkus`.

### SpotifyAccessTokenAdapter – token lifecycle

**Class:** `adapter-out-spotify/.../SpotifyAccessTokenAdapter`

* Returns cached `AccessToken` when it is not expiring soon (more than 5 minutes remaining)
* Refreshes the access token when it is expiring within the next 5 minutes
* Persists a rotated refresh token when Spotify returns a new `refreshToken` in the refresh response
* Retains the existing encrypted refresh token when Spotify does not rotate it
* Throws `IllegalArgumentException` with user ID context when user is not found in repository
* Throws `IllegalStateException` with user ID context when token refresh fails

### CurrentlyPlayingSkipPredicate – adaptive scheduler interval

**Class:** `adapter-in-scheduler/.../CurrentlyPlayingSkipPredicate`

* Skips execution when the upstream `ScheduledSkipPredicate` (starter) returns `true`
* Does NOT skip on the first call when no playback is active (slow interval)
* Skips on the second immediate call when no playback is active (slow interval not elapsed)
* Skips on the second immediate call when playback is active (fast interval not elapsed)
* Switches to fast interval (20 s) when `PlaybackActivityPort.isPlaybackActive()` returns `true`
* Switches to slow interval (5 min) when playback is no longer active

### TokenEncryptionAdapter – AES token encryption

**Class:** `adapter-in-web/.../TokenEncryptionAdapter`

* `encrypt` returns a non-blank ciphertext different from the plaintext input
* `decrypt` round-trips back to the original plaintext
* `decrypt` returns a `DomainError` for tampered or invalid ciphertext

### SlackNotificationAdapter – check failure notifications

**Class:** `adapter-out-slack/.../SlackNotificationAdapter`

* Sends an HTTP POST to the configured webhook URL when violations are present
* Formats the notification message with playlist name and violation list
* Does not send a notification when the check result has no violations

### SchedulerInfoAdapter – cronjob metadata extraction

**Class:** `adapter-out-scheduler/.../SchedulerInfoAdapter`

* Extracts display name from `@Scheduled` annotation `identity` attribute
* Falls back to the method name when no `identity` is provided
* Correctly extracts the last execution time from `ScheduledExecution` metadata

### MongoQueryMetrics – query duration tracking

**Class:** `adapter-out-mongodb/.../MongoQueryMetrics`

* Records query duration and increments per-collection counter
* Returns the correct `MongoQueryStats` aggregation (avg, max, count) after multiple samples
* Handles a clean slate (no recorded queries) without throwing
