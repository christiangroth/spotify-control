# spotify-control

# Introduction and Goals

## Requirements Overview

spotify-control is a private Spotify playlist manager built for exactly one user (see
[ADR-0008](../adr/0008-single-user-architecture.md)). There is no registration, no allow-list, and
no user-management UI — any Spotify account that completes the OAuth login flow becomes the
application's user, and a different account cannot log in once one is registered.

**Implemented features:**

1. **Playback Tracking** – Spotify `currently-playing` and `recently-played` are polled every 20 seconds and stored. Partial plays (tracks not played to completion) are detected via observation sessions and stored separately (see [ADR-0010](../adr/0010-partial-play-detection-via-currently-playing-polling.md)).

2. **Playlist Mirror** – Local copy of selected Spotify playlists. Sync is driven by snapshot IDs – a full track sync is only performed when Spotify reports a change.

3. **Catalog Sync** – Artist images, track album references, and album details (title, cover) are fetched from the Spotify API and stored in deduplicated `app_artist`, `app_track`, and `app_album` collections.

4. **Listening Statistics** – Playback data is aggregated (daily, weekly, monthly, quarterly, yearly) into a dashboard showing total play counts, daily play trends, top artists, top tracks, and recently played items.

5. **Shallow Artists** – Artists can be confirmed as `SYNC` (full catalog sync) or `SHALLOW` (artist metadata only, no albums/tracks). Newly discovered artists start in an `_ASSUMPTION` variant of one of these, guessed from discovery context, until the user confirms them via the settings UI. Playback events of `SHALLOW`/`SHALLOW_ASSUMPTION` artists are still recorded but excluded from statistics. See [ADR-0009](../adr/0009-shallow-artist-catalog-sync-model.md).

6. **Playlist Checks** – A set of pluggable rule checks runs against every actively-synced playlist after each sync: duplicate tracks, multiple tracks by the same artist on "singularity" playlists, year-playlist tracks missing from the master "all" playlist, and tracks referencing an outdated release when a newer/better version exists in the artist's catalog. Results are shown on a dashboard; some violations can be fixed directly from the UI. See [ADR-0011](../adr/0011-playlist-checks-framework.md).

## Quality Goals

| Priority | Goal | Motivation |
|----------|------|------------|
| 1 | **Maintainability** | The application is built and maintained by a single developer, with AI coding agents doing a significant share of the implementation work (see [ADR-0004](../adr/0004-using-ai-coding-agents.md)). Clear module boundaries, port contracts, and up-to-date documentation are what keep changes safe without a second reviewer. |
| 2 | **Operational Stability** | The application runs unattended against a third-party API with strict, partly undocumented rate limits. It must recover from rate limiting, token expiry, and transient failures without manual intervention. |
| 3 | **Domain Purity / Testability** | Business logic (sync rules, aggregation, playlist checks) must be verifiable without a running Quarkus instance or live Spotify/MongoDB access, so correctness can be checked quickly and cheaply during AI-assisted development. |

## Stakeholders

| Role/Name         | Contact     | Expectations                                                              |
|-------------------|-------------|---------------------------------------------------------------------------|
| Developer / User  | (private)   | The single user; operates and uses the application for personal use      |

# Architecture Constraints

- **Single user** – The application is built for exactly one user (see [ADR-0008](../adr/0008-single-user-architecture.md)). No registration, no user-management UI, no allow-list.
- **Login exclusively via Spotify OAuth** – No other authentication mechanism.
- **External MongoDB** – Data is stored in MongoDB Atlas (two projects: prod + dev). No self-hosted database.
- **VPS with Docker Swarm** – Deployment target is an existing VPS running Docker Swarm with Traefik for routing and TLS.
- **No separate frontend project** – Server-side rendering via Quarkus Qute; no React, Vue, npm, Node.js, or build steps.
- **All Spotify API calls via Outbox** – No direct Spotify calls outside `adapter-out-spotify`.

# Context and Scope

## Business Context

spotify-control interacts with the following external systems:

| External System     | Direction      | Description                                                              |
|---------------------|----------------|--------------------------------------------------------------------------|
| Spotify API         | bidirectional  | Read/write playback, playlists, artists, albums; OAuth 2.0 login        |
| MongoDB Atlas       | bidirectional  | Persistent storage for all domain data (tracks, playlists, events, etc.) |
| User (browser)      | bidirectional  | Web UI for dashboard, settings, and documentation                        |
| Slack               | outbound       | System, catalog and playlist-check notifications via incoming webhook    |

## Technical Context

| Interface             | Technology                                              |
|-----------------------|---------------------------------------------------------|
| Spotify API           | REST via `adapter-out-spotify`; OAuth 2.0 token refresh |
| MongoDB Atlas         | MongoDB driver via `adapter-out-mongodb`                |
| Web UI                | Quarkus Qute SSR, Vanilla JS (fetch API), Bootstrap 5, Server-Sent Events |
| Scheduled jobs        | Quarkus scheduler                                       |
| Internal event bus    | CDI Events (in-process)                                 |
| Async task queue      | Persistent Outbox (`de.chrgroth.quarkus.outbox`)        |
| Slack                 | REST POST via `adapter-out-slack`; incoming webhook     |

# Solution Strategy

- **Hexagonal Architecture** – The application is structured using hexagonal (ports and adapters) architecture to cleanly separate domain logic from infrastructure concerns.
- **Outbox Pattern** – All Spotify API operations are routed through a persistent outbox to ensure reliability and rate limit handling. No direct Spotify calls are made outside `adapter-out-spotify`.
- **Server-Side Rendering** – The frontend uses Quarkus Qute templates with vanilla JS (fetch API) for dynamic interactions, eliminating the need for a separate frontend project or JavaScript framework.
- **Single-User System** – The application is built for exactly one user, logging in via Spotify OAuth. No allow-list, no self-service registration, no user-management UI. See [ADR-0008](../adr/0008-single-user-architecture.md).

# Building Block View

## Whitebox Overall System

The system is composed of the following Gradle modules:

| Module                  | Role                                                                              |
|-------------------------|-------------------------------------------------------------------------------------|
| `adapter-in-http-frontend` | REST endpoints, OAuth callback, SSE endpoints, action endpoints                |
| `adapter-in-http-metrics`  | HTTP response timing/slow-response and application/playlist/catalog/outbox/MongoDB gauge metrics via Micrometer |
| `adapter-in-outbox`     | Outbox event dispatcher – routes outbox events to the correct domain port handler |
| `adapter-in-scheduler`  | Scheduled jobs for polling Spotify and syncing data                               |
| `adapter-in-starter`    | One-time startup bean implementations for data migrations and bugfixes            |
| `adapter-out-config`    | Reads MicroProfile config/env vars and masks sensitive keys for the `/config` health page |
| `adapter-out-mongodb`   | Repository implementations for MongoDB                                            |
| `adapter-out-outbox`    | Outbox adapter for writing new tasks into the outbox                              |
| `adapter-out-scheduler` | Scheduler info provider for the health page                                       |
| `adapter-out-slack`     | Slack notification adapter for system, catalog and playlist-check notifications via incoming webhook |
| `adapter-out-spotify`   | Spotify API client, token refresh, fixed-interval throttling, rate-limit backoff  |
| `application-quarkus`   | Quarkus application bundling and configuration                                    |
| `domain-api`            | Ports (interfaces) – defines the contracts between domain and adapters            |
| `domain-impl`           | Domain services and business logic                                                |

![Module overview](https://kroki.io/plantuml/svg/bVTBitwwDL37K0TuobTnUnanS2EPpYU59FDmoNhOxoxtBVtmui3z78V2JuMlOUV6enm2npQ8RcbAyVkRL8bPGNCBJDeT156P_GY1BC0Z_WR1Q3HkSZ4DOQ0cUluJZ1R0NX6CEW1sK0qPmCx_I89H81fDx097Z8I_AXBAeZkCJa--kqUAv86GdcYpKB0qdrAoL-ImBEqmAN0h0DXq0AFGGGospKWkoDvOxGZ8g-efr6Uca46zEQoZB4wauu_kJ3o5wDNbjIXm1LAq5MPqu-VYsZoC3aLWS_IcyHalg6b-6ofcScUBfqPCmXXoje_PzHM_BvKsvTpl-ase9llOczAyFtISb4iUeKA_hWJ8TTacKM9aJatDoZVsy8k7cWfUWADc3rf1Qg6NX7tSJe1xNvABlsS42RaRmm81fuQ77nlDibOfo5nK-zXcUlyemRqqKTneUhpPKPGeKZm14wol3uHl6Z_WPUA173DqPpzaTSu8W97WZTWh77-UYV_1UOLFomW0LVRu8w6oI2mh-7hbTNRngRYDG6Ta1QAPdxpwdaLF7p232KNPUZThczlEDeJRqljz8d2VHrLiSXuV_0b_AQ)

### `adapter-in-outbox`

Dispatches outbox events to the appropriate domain port handler. Implements `OutboxTaskDispatcher` – receives a deserialized `DomainOutboxEvent` and calls the correct `handle(event)` method on the domain port. Any `SpotifyRateLimitError` returned by a handler pauses that event's outbox partition until the `Retry-After` duration reported by Spotify has elapsed.

### `adapter-in-scheduler`

Contains Quarkus `@Scheduled` jobs that trigger domain actions at configured intervals. All jobs skip execution via `skipExecutionIf = StarterSkipPredicate::class` until all starters have completed.

### `adapter-in-starter`

Contains concrete `Starter` implementations acting as inbound adapters: they receive a startup trigger from `de.chrgroth.quarkus.starters` and call into the domain via port interfaces. Each starter executes exactly once in production mode. Used for one-time data migrations, schema changes, and bugfixes.

### `adapter-in-http-frontend`

Handles all inbound HTTP interactions: the web UI (Qute templates), OAuth callback, SSE streams for live updates, and settings action endpoints. Records response timings via the `ResponseTimingPort` (`domain-api`), without depending on `adapter-in-http-metrics` directly.

### `adapter-in-http-metrics`

Records HTTP response timings and slow-response detection via Micrometer (`HttpResponseMetrics`), which implements the `ResponseTimingPort` from `domain-api`. Independent of the frontend so it can evolve separately (e.g. metrics caching), and wired together only in `application-quarkus` via CDI. Also registers the playlist overview gauges (`PlaylistMetrics`), catalog gauges (`CatalogMetrics`), outbox backlog gauges (`OutboxMetrics`), MongoDB collection size gauges (`MongoCollectionMetrics`), and the static application info gauge (`ApplicationInfoMetrics`) – the domain-facing ones read counts through the `PlaylistStatsPort`/`CatalogStatsPort`/`OutboxStatsPort`/`MongoCollectionStatsPort` from `domain-api`, while the underlying `PlaylistStatsCache`/`CatalogStatsCache`/`OutboxPartitionStatsCache` stay in `domain-impl` (or `MongoStatsAdapter` in `adapter-out-mongodb` for MongoDB collection stats), since the "out of sync"/"pending album upgrade" classification, catalog counting, and outbox partitioning are domain rules, not a metrics concern. `MongoStatsAdapter` caches and refreshes on the same 15s schedule so gauge reads and `HealthService`'s health page/SSE reads share a single collStats call per collection instead of each querying MongoDB independently. `PlaylistMetrics`, `CatalogMetrics`, and `OutboxMetrics` share a single port read per scrape via `ScrapeSnapshot`, a small TTL-memoized helper local to this module.

### `adapter-out-config`

Implements `ConfigurationInfoPort`. Reads all MicroProfile config properties and environment variables, masking sensitive keys (`app.health.masked-config-keys`/`app.health.masked-env-keys`), to power the `/config` health/debug page.

### `adapter-out-mongodb`

Implements all repository interfaces defined in `domain-api`. Manages the MongoDB collections for the user (including encrypted token storage), tracks, artists, albums, playlists, playback events and aggregations, playlist checks, and sync traces.

#### MongoDB Collections

| Collection                        | Description                                                                                                                     |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `app_album`                       | Deduplicated album metadata: title, cover image, main artist reference, lastSync.                             |
| `app_artist`                      | Deduplicated artist metadata: name, imageLink, lastSync, syncStatus (SYNC/SHALLOW/SYNC_ASSUMPTION/SHALLOW_ASSUMPTION). |
| `app_playback`                    | Processed playback events combining recently played and partial played data.                                                    |
| `app_playback_aggregation`        | Precomputed daily/weekly/monthly/quarterly/yearly ranking aggregations, keyed by `"${type}:${periodStart}"`.               |
| `app_playlist_check`              | Per-playlist, per-check results (pass/fail, violations), keyed by `"${playlistId}:${checkId}"`.                            |
| `app_track`                       | Deduplicated track metadata: title, main artist reference, additional artist references, album reference, lastSync.   |
| `app_user`                        | Spotify user profile with encrypted access and refresh tokens.                                                                  |
| `outbox`                          | Persistent outbox task queue (managed by `de.chrgroth.quarkus.outbox`).                                                        |
| `outbox_archive`                  | Archived completed/failed outbox tasks (managed by `de.chrgroth.quarkus.outbox`).                                              |
| `outbox_partitions`               | Outbox partition pause/resume admin state.                                                                                      |
| `spotify_currently_playing`       | Currently playing track observations.                                                                                           |
| `spotify_playlist`                | Full playlist data including all tracks.                                                                                        |
| `spotify_playlist_metadata`       | Playlist metadata: name, snapshot ID, type (ALL/YEAR/SINGULARITY/UNKNOWN), sync status.                                     |
| `spotify_recently_partial_played` | Partial play events (plays that did not complete a full track).                                                                 |
| `spotify_recently_played`         | Raw recently played track events (append-only).                                                                                 |
| `starters`                        | One-time startup bean execution state (managed by `de.chrgroth.quarkus.starters`).                                             |
| `sync_trace`                      | Audit log of why a catalog entity was enqueued for sync (discovery cause).                                                       |

### `adapter-out-outbox`

Implements `OutboxPort` and `OutboxManagementPort`. Bridges the domain to the `de.chrgroth.quarkus.outbox` library for writing and managing outbox tasks.

### `adapter-out-scheduler`

Implements `CronjobInfoPort`. Provides scheduled job metadata (name, next execution, running state) to the health page via the Quarkus `Scheduler` API.

### `adapter-out-slack`

Sends notifications to a configured Slack incoming webhook. Observes Quarkus `StartupEvent` and `ShutdownEvent` lifecycle events and implements `OutboxPartitionObserver` to react to partition pause/resume events, and `PlaylistCheckNotificationPort` to react to playlist-check pass/fail changes. Each notification type is individually enabled via configuration properties. The webhook URL is sensitive and must be set via the `SLACK_WEBHOOK_URL` environment variable in production.

### `adapter-out-spotify`

Encapsulates all communication with the Spotify Web API. Handles token refresh, a shared fixed-interval throttle (`spotify.throttle.default-interval-ms`, default 10s) applied to the `to-spotify-catalog` and `to-spotify-playlist` outbox partitions, and rate-limit backoff based on the `Retry-After` header of a `429` response. All catalog and playlist endpoints operate on a single entity per call (e.g. `GET /v1/artists/{id}`, `GET /v1/albums/{id}`) — there is no bulk multi-ID endpoint usage.

### `application-quarkus`

Bundles all modules into the runnable Quarkus application. Contains test infrastructure and integration tests (`@QuarkusTest`).

### `domain-api`

Defines all port interfaces (`port.in.*`, `port.out.*`), domain models, outbox event types (`DomainOutboxEvent`), and outbox partitions (`DomainOutboxPartition`).

### `domain-impl`

Contains the core business logic: playback data processing and aggregation, playlist synchronization and checks, artist catalog management, user profile handling, dashboard statistics computation, and token encryption.

### External Dependencies

#### `de.chrgroth.quarkus.outbox`

Provided via [christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox) (GitHub Packages). Three artifacts:

- `domain-api` – outbox contracts: `OutboxPartition`, `OutboxEvent`, `OutboxTaskDispatcher`, `OutboxTaskResult`, `RetryPolicy`, and associated types
- `domain-impl` – Quarkus implementation: `OutboxImpl`, `OutboxProcessor`, `OutboxWakeupService`, `OutboxStartupRecovery`, `OutboxPartitionWorker`
- `adapter-out-persistence-mongodb` – MongoDB persistence: at-least-once delivery, atomic claim, partition-level pause/resume, task deduplication, priority ordering

#### `de.chrgroth.quarkus.starters`

Provided via [christiangroth/quarkus-one-time-starters](https://github.com/christiangroth/quarkus-one-time-starters) (GitHub Packages). Three artifacts:

- `domain-api` – contracts: `Starter`, `StarterSkipPredicate`, `StarterCompletionFlag`
- `domain-impl` – execution orchestration and startup observer
- `adapter-out-persistence-mongodb` – MongoDB persistence for starter execution state

## Level 2

Not applicable. The module descriptions in the Whitebox Overall System above already give each
module's responsibilities at the level of detail useful for this system's size; a separate
whitebox decomposition per module would repeat the same information in diagram form without
adding anything.

# Runtime View

## Playback Polling Flow

`PlaybackDetectionJob` runs every 20 seconds and enqueues a single `FetchPlaybackData` event on the
`to-spotify-playback` partition (no throttle). Its handler fetches both currently-playing and
recently-played data in one pass — there is no separate 10-minute "recently played" schedule.

![Playback polling sequence](https://kroki.io/plantuml/svg/lVRNb9swDL37VxA92VizLDsGyNBsaYcNGNYhvfQUMBJtC5EllaLTeb9-kOMUcdNi3c22Ht8H8eSrKMjSNjaLO-MCMjbQeOdVzb4hEG7p5CTWqP2jcRWUaOPpiaYSWys33sna_CGYzU7H6KElp2jJ7B_vaqN2jmKEWZYFZDHKBHQCF79a5F0bs7WqSbeW-AIwQkxvY-CtxW6Larcm3htFPSzgGPOzla3_neU_vKv86nPRg_zh4wi4Dl5M2cHy9ttB7_CeaRTcYiS4GCj60yY9Z9liAdd74g4-foBIyjsdIT_aWpGQEuPdd78tYLHI-ggw-QQBYQ7kHlpq6YZE1U8jKJgXWcCEOriEOZxBIBc_GQxOwnBwCc6D1OxFLBW9txdQ0IdOrpKjQeJoqUanLeVnekdHAxnM4ev1HUz3s2lD08RMPFUtMzmxBynjqmGm3xTMoQ2RWI4Umyf4ZoBDHinG5EsY1c64qnjGoLzbJwrPoUZHeqp8EywJaRhG44nJDZMaBFJitL0Q6ewNWY6jk2Fk7ANDIKfB0SMYoeYV0ZcmjwmYGjQuhSZrKrO19F8JxqSa0g5At8EahUIwoCPENhBH0qRh28HzUM9bNvQRln28cd1MCei6PrLu-5DapX1K8ZZCnVMWL-8UQ9g8NTUv2TevbRbe_WtNEI1TBBajDPRnF2vdOfXFO2FvLfH72Dl14_ku9S_mRYImxJLFRFmRoLERAnHag-1Am6j8npg0YA8ZXUuFgtZXZ5rLqmKqUGh051fL-0vQ2BU9P5YlqdRrjR3khz0X2RU5nf7QfwE)

```
PlaybackDetectionJob (every 20s)
    → enqueue FetchPlaybackData (to-spotify-playback partition)
    → handle(FetchPlaybackData):
        fetchCurrentlyPlaying():
            Spotify GET /v1/me/player/currently-playing
            → session tracking + orphaned-session conversion into spotify_recently_partial_played
        fetchRecentlyPlayed():
            Spotify GET /v1/me/player/recently-played
            → new items → spotify_recently_played
            → remaining eligible sessions converted → spotify_recently_partial_played
            → duplicates superseded by recently-played are deleted
    → if any new data: enqueue AppendPlaybackData (domain partition)
```

## Currently Playing → Partial Play Conversion

See [ADR-0010](../adr/0010-partial-play-detection-via-currently-playing-polling.md) for why this
exists. On every `PlaybackDetectionJob` run:

- `fetchCurrentlyPlaying()` tracks the current session in `spotify_currently_playing`. If the
  currently-playing track differs from a still-tracked session (or restarted from the beginning),
  the previous session's observations are considered "orphaned" and eligible for conversion.
- `fetchRecentlyPlayed()` additionally converts any remaining tracked session that is not the most
  recently observed one (which may still be active) and whose observed progress exceeds the
  configured minimum (default: 25 seconds).
- For each convertible session, a `RecentlyPartialPlayedItem` is created with an estimated play
  duration and written to `spotify_recently_partial_played`. Converted and completed track
  observations are then deleted from `spotify_currently_playing`.
- If a partial play is later superseded by a matching `recently_played` entry (same track, close
  start time), the partial-play record and any already-appended `app_playback` entry are deleted
  and the affected day is re-aggregated.

## Playback Data Processing Flow

Raw playback data from `spotify_recently_played` and `spotify_recently_partial_played` is processed
into the normalised `app_playback` collection by `PlaybackService`. There are two modes:

**Append (triggered automatically):** After new raw data arrives, `AppendPlaybackData` is enqueued
on the `domain` partition. All playback events are stored unconditionally regardless of artist sync
status — filtering by status happens only at aggregation time (see [Artist Sync Status](#artist-sync-status)).
The handler fetches all source items newer than the most recent `app_playback` entry, deduplicates
against existing `app_playback` timestamps, then:
1. Appends new entries to `app_playback` (playedAt, trackId, secondsPlayed).
2. Enqueues `AggregatePlaybackData(DAY, day)` for each affected day.
3. Calls `SyncController.syncForTracks()` with a `CatalogSyncRequest` per newly-seen track – for any
   artist not yet present in `app_artist`, this enqueues `SyncArtistDetails(artistId, fromPlaylist = false)`
   on the `to-spotify-catalog` partition (see [Catalog Sync Flow](#catalog-sync-flow)).

**Rebuild (user-triggered from Settings):** Deletes all `app_playback` entries and re-runs the
append logic from scratch over all source data.

## Artist Sync Status

The catalog settings pages (`/catalog/artists/settings` for undecided artists, the Catalog UI for
confirmed ones) let users control how much of an artist's catalog is synced via `app_artist.syncStatus`
(`ArtistSyncStatus`) — see [ADR-0009](../adr/0009-shallow-artist-catalog-sync-model.md) for the
rationale:

| Status               | Description                                                                                                                                          |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SYNC`                | Final status. Full catalog sync (albums, tracks); the artist's playback events are included in aggregation/statistics.                              |
| `SHALLOW`             | Final status. Only the artist document itself is synced — no albums or tracks. Existing albums/tracks whose main artist is this artist are deleted on transition to this status. Playback events are still stored but excluded from aggregation. |
| `SYNC_ASSUMPTION`     | Automatic guess for a newly discovered artist found via an actively synced playlist. Behaves like `SYNC` for catalog sync purposes until confirmed.  |
| `SHALLOW_ASSUMPTION`  | Automatic guess for a newly discovered artist seen only via playback/recently-played history. Behaves like `SHALLOW` until confirmed.                |

Assumption statuses are assigned automatically on first discovery of an artist and can only
transition into one of the two final statuses via the settings UI (`CatalogPort.setArtistSync`/
`setArtistShallow`) — never back into an assumption status, and there is no direct transition
between the two assumption statuses. An artist found on an actively-synced playlist while still in
an assumption status is automatically promoted to `SYNC` (`promoteAssumptionArtistsFoundOnPlaylist`).

For albums/tracks with multiple artists, only the main artist (first artist in the Spotify artist
list) determines sync scope and deletion on transition to `SHALLOW`; secondary/featured artists are
unaffected.

Playback events (`app_playback`) are never deleted based on artist status — the append flow always
stores them unconditionally (see above). `PlaybackAggregationService.aggregateDay()` instead filters
out items whose track's main artist is `SHALLOW`/`SHALLOW_ASSUMPTION` at aggregation time. Switching
an artist's status triggers `rebuildAllAggregations()` so the change is reflected in statistics
without needing to re-ingest playback data.

The daily rotating catalog resync (see [Catalog Sync Flow](#catalog-sync-flow)) only re-enqueues
album sync for artists in `SYNC`/`SYNC_ASSUMPTION`; `SHALLOW`/`SHALLOW_ASSUMPTION` artists are never
automatically resynced.

## Playlist Sync Flow

![Playlist sync sequence](https://kroki.io/plantuml/svg/jVRhb9MwEP2eX3Hap0Tq6Cq-FRWtdBsUaWOQDgkJabrF18aq4_PsS0tA_HfkNKnabSC-xb537957tnMeBL3UlUnCWluHHiuo2HJReq4IxNd0UAklKt5qu4IlmnBYUbTE2sgVW8n1T4LR6LCNHmuyBU295-2i1MXaUggwShKHXnShHVqBk881-nUdkrwoSdWG_AlggBBXx8Bbg43RQXLyG11QC3N4jPlUywP_SNJrtiu-eJe1IN5tHgFzx6KXDUxv57t5u3WiUPABA8FJR9FWq_idJJMJfODamwZQYPz6DNK9psYWH_khg8kkaaXD6VtwCGMg-1hTTXdOoVBIs8RhrO00wRhiZ88yt0uGVPi0U3PqusIARmcBpPQsYihrlbwAg9aiaLZRRzeiF1KiVYbSp_N6QR0XjOH95QKGm9GwomFPHDpQmwOMwZMzWFDfdN_j7isSjBFCarGiAQSLLpQsczUAaVzcaWyRC0od_hnFBQqmPe1cZeDIw3S2mH-9hL3drZYSihLtitR-VPIfxiP7343vXQ9_afV7KB6LdYDU4UpbFFJvvltP7QsKsPRcgcMVwQjY7kVApUOFUpTZk-QCbmiIzpFVz9KD3aSXYpmxFc_GkH8VA7xiv2ixaRahETH1Eo2RoDahTcvS1jSgdCh4Q54UYAs5umAFChpePVXpPFcsBPfTPL-7vl3MP9103QGWXFsFJXkCYci_3cwgne1oupf57GC_1LZPflZSlH14sqniCrUdxPwMtrd4RcDWNFlyTlbF39Qf)

```
PlaylistSyncJob (hourly at :30)
    → enqueue SyncPlaylistInfo (to-spotify-playlist partition, 10s throttle)
    → Spotify GET /v1/me/playlists → replace spotify_playlist_metadata, compare snapshot IDs
    → for each ACTIVE playlist with a changed snapshot: enqueue SyncPlaylistData

SyncPlaylistData (to-spotify-playlist partition)
    → Spotify GET /v1/playlists/{id}/tracks (paginated; restarts from page 1 on snapshot mismatch)
    → save/append spotify_playlist tracks
    → SyncController.syncForTracks() → SyncArtistDetails per newly discovered artist
    → promote any _ASSUMPTION-status artist found here to SYNC
    → on last page: enqueue RunPlaylistChecks(playlistId) (domain partition)
```

## Catalog Sync Flow

See [ADR-0009](../adr/0009-shallow-artist-catalog-sync-model.md). All catalog sync calls are
single-entity (`GET /v1/artists/{id}`, `GET /v1/artists/{id}/albums`, `GET /v1/albums/{id}`) —
there is no bulk multi-ID sync. Two independent paths feed into the same `to-spotify-catalog`
partition:

![Catalog sync sequence](https://kroki.io/plantuml/svg/lVRtT9swEP6eX3FCmpSIVmkmPlUqooO9MMHaqaAJCQldnEtj1bGDfQGyafvtU5yUtdAx9s3ne3vuucc-coyW61IFbiV1hRZLKI02orCmJGBb04bHFZiZe6mXkKNym56McqwVfzCaF_I7QZJsptFtTVrQ1Fpzf1FIsdLkHCRBUKFlKWSFmmHva412VbtgIQrKakV2D9CBa63twEWjxbHRbI1SZCEOjpFRmeWC7J0U5NMEbufMak7NQxCeG700J-8iH2S6y-3ilWGZNzCdn3b9OzvIkDFFR7DXl_Desj0HwWQCMz3MqESdQSadMHdkGwhza0qoFDYpihUY689KOgbXaBHBZBIIhOFhjwTG0M42tSwdnxCjVC5Eb51mA2irzfsCEeTGQudz11obhoYYpAasqpvuHkI2wx7_UHQcDSAZOeDCGmZFURD0nYeHIBDGUKDOFIXPYEQ90L4cjOHj-wuI75K4xxD_kNnPPsizAmOoK0eWtxAtrr4c30wXi8vz-cXp7AvIfGuswbUm5QgWn6ZnZ7NvG5HR35maqrQu_xAVtUUdI9cOpIO2Y_ykrd_YCUrVADKM3o5HowFYw8ituL0eWBoNYddgra9Gi88m9WvzsnxkjfRtTTVtwmmDwwybWX5FaOENJAcDYMOo5uvybpIcRE8oc6RI8E7Q6223S-ZCug2cTklB1zr8lcTJARdgci8wTBU9ZlVkIcPmf3hsUzpAlPV1PHPPZbXJmSvQUgZpA6nhAirkwgGm5o48c_8SXIfidXqL0QdDWOFSamTKdo_XRj2-p9ZYT-cNePp62stX0zQATQ98aZXXXWksQYVLci8-rQ1AL0zqG_lBYX_XbcwWxWrX-LteoB913599XnBEOmu__t8)

```
On-demand (from playback or playlist sync):
    SyncController.syncForTracks() → enqueue SyncArtistDetails(artistId, fromPlaylist)
        for any artist not yet in app_artist (to-spotify-catalog, 10s throttle)

    handle(SyncArtistDetails):
        Spotify GET /v1/artists/{id}
        → upsert app_artist (SYNC_ASSUMPTION if fromPlaylist, else SHALLOW_ASSUMPTION)
        → enqueue SyncArtistAlbums(artistId) if status is SYNC/SYNC_ASSUMPTION

Daily at 02:00, rotating partition (ArtistCatalogSyncJob):
    partition = dayOfYear % 14
    → CatalogService.enqueueArtistAlbumsSync(partition, totalPartitions=14)
    → selects SYNC/SYNC_ASSUMPTION artists in this partition slice (~1/14th per day)
    → enqueue SyncArtistAlbums(artistId) per selected artist

handle(SyncArtistAlbums) (to-spotify-catalog, shared by both paths above):
    Spotify GET /v1/artists/{id}/albums (paginated)
    → enqueue SyncAlbumDetails(albumId) per album not yet in app_album
    → enqueue SyncArtistAlbums(artistId, nextUrl) if more pages

handle(SyncAlbumDetails):
    Spotify GET /v1/albums/{id} + GET /v1/albums/{id}/tracks (paginated)
    → upsert app_album + app_track
```

The daily job spreads full-catalog resync load across two weeks instead of resyncing every artist
at once, and only ever re-enqueues album sync — it never triggers `SyncArtistDetails`, which is
exclusively an on-demand discovery event.

## Playlist Checks Flow

See [ADR-0011](../adr/0011-playlist-checks-framework.md). `RunPlaylistChecks` is purely
outbox/event-driven — it is never triggered directly by a scheduler job, only enqueued at the end
of `SyncPlaylistData` for a changed playlist (see [Playlist Sync Flow](#playlist-sync-flow)) or
after a user-triggered fix. Its handler (`PlaylistCheckService`) looks up all CDI-discovered
`PlaylistCheckRunner` beans, filters them by `isApplicable(playlistInfo)` (some checks are scoped to
a specific playlist type, e.g. the "single artist track" check only runs for `SINGULARITY`
playlists), and executes the applicable runners concurrently. Each result is upserted into
`app_playlist_check` (deterministic id `"$playlistId:$checkId"`); a Slack notification fires only
when a check's pass/fail state flips or its violation list changes, to avoid repeat-notification
noise. A fixable check's "Fix" action calls the runner's `fix()` against the live Spotify API and,
on success, re-enqueues `SyncPlaylistData` to pull the corrected state back down and re-run checks.

## Playback Aggregation Flow

Statistics are served from precomputed aggregations rather than scanning raw `app_playback` data on
every read. `PlaybackAggregationJob` enqueues one `AggregatePlaybackData(type, periodStart)` event
per period type on independent schedules (daily 01:00, weekly Monday 01:30, monthly 1st 02:00,
quarterly 02:30, yearly Jan 1 03:00 UTC), plus `AppendPlaybackData` enqueues `DAY` aggregations
directly for any day whose raw data changed. Aggregation is a two-tier rollup:

- **`DAY`** is computed directly from raw `app_playback` items, resolving track/artist metadata and
  filtering out `SHALLOW`/`SHALLOW_ASSUMPTION` artists (see [Artist Sync Status](#artist-sync-status)).
- **`WEEK`/`MONTH`/`QUARTER`/`YEAR`** are each computed by merging the `DAY` documents in their
  range — never by re-scanning raw playback data, and not chained through intermediate tiers (e.g.
  `YEAR` merges `DAY`s directly, not `QUARTER`s). Only the top 25 rank entries are persisted for
  these rolled-up periods to keep documents small.

All five tiers share one `app_playback_aggregation` collection, keyed by `"${type}:${periodStart}"`.
`rebuildAllAggregations()` deletes and fully re-enqueues all tiers when historical data changes (e.g.
an artist's sync status flips).

# Deployment View

## Infrastructure Level 1

The application is deployed on an existing VPS running Docker Swarm. Traefik handles routing, TLS termination, and HTTPS. MongoDB is hosted externally on MongoDB Atlas.

| Component       | Technology              | Notes                                      |
|-----------------|--------------------------|--------------------------------------------|
| Application     | Quarkus (native Docker) | Deployed as a Docker Swarm service         |
| Reverse Proxy   | Traefik                 | TLS via Let's Encrypt, already provisioned |
| Database        | MongoDB Atlas           | Two projects: prod + dev                   |

## Infrastructure Level 2

Secrets are never stored in deployment configuration – always provided via environment variables from a `.env` file that is not checked into Git.

### Environments

|                     | Local                          | Production                |
|---------------------|---------------------------------|----------------------------|
| MongoDB             | Atlas Dev Cluster              | Atlas Prod Cluster        |
| Quarkus Profile     | `dev`                          | `prod`                    |
| Spotify Redirect    | `localhost:8080`               | `spotify.yourdomain.com`  |
| Container           | no (direct Quarkus start)      | Docker Swarm              |

Quarkus profile is controlled via environment variable:

```bash
QUARKUS_PROFILE=prod
```

### Deployment Workflow

Build the application as a Quarkus native Docker image, push to the GitHub Container Registry, copy the Docker stack file to the VPS via SCP, and deploy via Docker Swarm stack.

### Release Process

- **Release plugin** – `net.researchgate.release` manages version bumping and Git tagging
- **Release-Notes plugin** – custom Gradle plugin (`de.chrgroth.gradle.plugins.release-notes`) maintained in https://github.com/christiangroth/gradle-release-notes-plugin
- **CI/CD** – the GitHub Actions workflow (`gradle.yml`) runs `./gradlew build` on every push; runs `./gradlew release` only on pushes to `main`; after release, the Docker stack file is copied to the VPS via SCP and the stack is deployed via SSH. All secrets (including `SLACK_WEBHOOK_URL`) must be configured as GitHub Actions repository secrets.
- **Snippet requirement** – every branch that is not `main` or `dependabot/*` **must** contain at least one release note snippet in `docs/releasenotes/snippets/`; the build fails without it. Create snippets with the corresponding Gradle tasks (`releasenotesCreateFeature`, `releasenotesCreateBugfix`, …); filenames follow the pattern `{branch-last-segment}-{type}.md`

### Spotify OAuth Redirect URIs

Both URIs must be registered in the Spotify Developer App (replace `spotify.yourdomain.com` with the actual production domain):

```
https://spotify.yourdomain.com/oauth/callback   ← Production
http://localhost:8080/oauth/callback             ← Local development
```

# Cross-cutting Concepts

## Testing Strategy

Tests follow the *Test Your Boundaries* principle mapped to the hexagonal architecture:

| Layer | Entry point | Test doubles | Module | Framework |
|-------|-------------|--------------|--------|-----------|
| 1 – Domain logic | Inbound port (`*Port` in `domain-api`) | MockK mocks for all outbound ports | `domain-impl` | JUnit 5 + MockK |
| 2 – Outbound adapters | Outbound port interface | None – real infra (MongoDB dev-service, Spotify mock) | `application-quarkus` | `@QuarkusTest` |
| 3 – Inbound adapters | HTTP endpoint / scheduler `run()` | CDI mocks via `@InjectMock` | `application-quarkus` | `@QuarkusTest` + REST Assured |
| 4 – App wiring | Health/metrics endpoints | None | `application-quarkus` | `@QuarkusTest` |
| 5 – Adapter-local logic | Class under test | MockK mocks | individual adapter module | JUnit 5 + MockK |

Layer 5 applies to adapter modules where the logic is pure (e.g. `adapter-in-starter`, `adapter-out-scheduler`).

## Authentication and Access Control

- Spotify OAuth 2.0 Authorization Code Flow.
- A `User` document is upserted in the `app_user` MongoDB collection on every successful login. Both access and refresh tokens are stored encrypted (AES-256-GCM) using `APP_TOKEN_ENCRYPTION_KEY`.
- The application is built for a single user (see [ADR-0008](../adr/0008-single-user-architecture.md)); login does not check an allow-list — any Spotify account that completes the OAuth flow is upserted as the application's user. A different Spotify account cannot log in once one user is already registered.
- Scheduled jobs, catalog sync, and every repository port act directly on the one stored user instead of fanning out over or filtering by a user list. MongoDB collections do not carry a `spotifyUserId` field or key, since only one user can ever exist.
- Session-based authentication for all endpoints. The session stores only the Spotify user ID – never tokens.
- `return_to` parameter stored in the session for redirect after login.
- A CSRF `state` parameter is generated per authorization request and validated in the callback.
- Token refresh is handled by `adapter-out-spotify` before each Spotify API call; the refreshed token is persisted back to MongoDB.

## Error Handling

All domain failures are represented as typed `DomainError` values wrapped in Arrow's `Either<DomainError, T>`.

- Port interfaces return `Either<DomainError, T>` instead of raw domain objects or throwing exceptions.
- Infrastructure adapters (`adapter-out-*`) catch all exceptions at the adapter boundary and convert them to typed `Either.Left<DomainError>` values – no exceptions cross port boundaries.
- Domain services compose multiple fallible operations using the Arrow `either { }` DSL with `bind()`.
- Web adapters translate `Either.Left<DomainError>` to HTTP error responses (redirect with `?error=<code>`).
- Error codes follow the convention `<AREA>-<NNN>` (e.g. `AUTH-001`). Codes are stable once published.

## Outbox Pattern

All Spotify API operations and domain-level async tasks are routed through a persistent outbox. This ensures reliability and decouples producers from consumers.

**Partitions and event types:**

| Partition             | Throttle | Rate-limit pause | Event Types                                                                 |
|------------------------|----------|-------------------|-------------------------------------------------------------------------------|
| `to-spotify-catalog`  | 10s (shared, runtime-adjustable) | yes | `SyncArtistDetails`, `SyncArtistAlbums`, `SyncAlbumDetails` |
| `to-spotify-playlist` | 10s (shared, runtime-adjustable) | yes | `SyncPlaylistInfo`, `SyncPlaylistData`                       |
| `to-spotify-user`     | none     | yes               | `UpdateUserProfile`                                                          |
| `to-spotify-playback` | none     | yes               | `FetchPlaybackData`                                                          |
| `domain`              | none     | n/a (no Spotify calls) | `RebuildPlaybackData`, `AppendPlaybackData`, `ResyncCatalog`, `RunPlaylistChecks`, `AggregatePlaybackData` |

`to-spotify-catalog` and `to-spotify-playlist` share one runtime-adjustable throttle interval
(`spotify.throttle.default-interval-ms`, default 10s) — there is no per-partition distinct value.
Rate-limit pause is not a per-partition configuration switch: any domain operation that returns a
`SpotifyRateLimitError` (any handler that calls Spotify, regardless of partition) pauses that
event's partition until the `Retry-After` duration has elapsed.

Successfully processed events are moved to `outbox_archive` (audit log). Internal triggers between services use CDI events (not the outbox).

## Server-Sent Events (SSE) and Live Updates

Backend services notify SSE streams via CDI events. The SSE endpoint delivers the initial state on connect, then pushes named update events to connected clients via a single shared reactive stream — there is only ever one possible subscriber (see [ADR-0008](../adr/0008-single-user-architecture.md)), so `DashboardSseAdapter` holds one emitter list rather than a per-user map.

## Scheduler Jobs

| Job                          | Interval                                | Outbox Event(s)                              |
|------------------------------|-------------------------------------------|-----------------------------------------------|
| `PlaybackDetectionJob`       | every 20 seconds                          | `FetchPlaybackData` → auto-enqueues `AppendPlaybackData` |
| `PlaylistSyncJob`            | hourly (at :30)                           | `SyncPlaylistInfo`                            |
| `ArtistCatalogSyncJob`       | daily at 02:00 (rotating 1/14 partition)  | `SyncArtistAlbums`                            |
| `UserProfileUpdateJob`       | daily at 04:00                            | `UpdateUserProfile`                           |
| `PlaybackAggregationJob`     | daily 01:00 / weekly Mon 01:30 / monthly 1st 02:00 / quarterly 02:30 / yearly Jan 1 03:00 | `AggregatePlaybackData` (one per period type) |

All scheduler jobs skip execution via `skipExecutionIf = StarterSkipPredicate::class` until all starters have completed successfully.

## Starters

One-time startup beans for data migrations, schema changes, and one-time bugfixes. Each starter executes exactly once in `NORMAL` (prod) mode; failed starters are retried on the next application start. The Quarkus scheduler is blocked until all starters succeed.

## Frontend Approach

No separate frontend project. The UI is rendered server-side using Quarkus Qute templates. Dynamic interactions are handled via vanilla JS with the fetch API. No React, Vue, npm, Node.js, or build steps are required.

**Technology stack:**
- Templates: Qute (Quarkus SSR)
- CSS: Bootstrap 5 via WebJar
- Interactivity: Vanilla JS (fetch API)
- Icons: hand-authored inline SVG `<symbol>` sprite defined in `layout.html`, referenced via `<use href="#icon-...">` — no icon-font dependency
- Live Updates: Server-Sent Events via native `EventSource` API
- Markdown rendering: marked via WebJar (docs and release notes pages)

## Documentation and Release Notes Serving

Architecture documentation (`docs/arc42`), ADRs (`docs/adr`), and release notes (`docs/releasenotes`) are served to the logged-in user directly from the application. A Gradle copy task bundles the Markdown files into the `adapter-in-http-frontend` classpath at build time. A `DocsResource` endpoint reads and passes the raw Markdown to Qute templates; the `marked` WebJar renders it in the browser.

## Configuration

All sensitive configuration is provided via environment variables:

```
SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
MONGODB_CONNECTION_STRING
APP_TOKEN_ENCRYPTION_KEY
SLACK_WEBHOOK_URL
```

# Architecture Decisions

| ADR | Title |
|-----|-------|
| [0001](../adr/0001-using-arc42-as-project-documentation.md) | Using arc42 as Project Documentation |
| [0002](../adr/0002-backend-hexagonal-architecture.md) | Backend: Hexagonal Architecture |
| [0003](../adr/0003-no-separate-frontend-project.md) | No Separate Frontend Project |
| [0004](../adr/0004-using-ai-coding-agents.md) | Using AI Coding Agents |
| [0005](../adr/0005-markdown-rendering-library.md) | Markdown Rendering Library: marked |
| [0006](../adr/0006-error-handling-concept.md) | Error Handling: Arrow Either&lt;DomainError, T&gt; |
| [0007](../adr/0007-persistent-outbox-pattern.md) | Persistent Outbox for Spotify API Operations |
| [0008](../adr/0008-single-user-architecture.md) | Single-User Architecture |
| [0009](../adr/0009-shallow-artist-catalog-sync-model.md) | Shallow Artist Catalog Sync Model |
| [0010](../adr/0010-partial-play-detection-via-currently-playing-polling.md) | Partial Play Detection via Currently-Playing Polling |
| [0011](../adr/0011-playlist-checks-framework.md) | Playlist Checks Framework |

# Quality Requirements

## Quality Requirements Overview

| ID  | Quality Goal              | Priority | Description |
|-----|--------------------------|----------|-------------|
| Q1  | Domain Purity            | High     | The domain (`domain-api`, `domain-impl`) has zero compile-time dependencies on infrastructure frameworks (Quarkus, MongoDB, Spotify SDK), with the exception of CDI and MicroProfile Config which are permitted in `domain-impl` service classes. Domain model classes are plain Kotlin data classes. |
| Q2  | Boundary Correctness     | High     | All communication between domain and adapters goes through port interfaces. No adapter type leaks into domain objects. No business logic lives in adapter classes. |
| Q3  | Outbox Reliability       | High     | All Spotify API calls are dispatched through the persistent outbox. Rate-limit handling and at-least-once delivery are guaranteed by the outbox implementation. |
| Q4  | Functional Test Confidence | Medium | The test suite covers the inbound port boundary (domain logic), outbound adapter round-trips (MongoDB), and inbound HTTP contracts. Line coverage is a by-product, not a goal. |
| Q5  | Maintainability          | Medium   | Any developer familiar with hexagonal architecture can understand and safely change the system. Module naming, port contracts, and architecture decision records provide the context. |
| Q6  | Operational Stability    | Medium   | The application handles Spotify rate limits, token expiry, and partial data gracefully without requiring manual intervention. |

## Quality Scenarios

| ID  | Scenario | Expected Behaviour |
|-----|----------|--------------------|
| Q1-S1 | A developer adds a MongoDB `Document` field to a domain model class | The build fails – domain model classes must not carry infrastructure types |
| Q2-S1 | A developer adds a direct Spotify HTTP call inside `domain-impl` | The build fails – `adapter-out-spotify` is not in the compile classpath of `domain-impl` |
| Q3-S1 | A `to-spotify-*` outbox partition receives a 429 response | The partition stops dispatching; a Slack notification is sent; tasks resume automatically once the `Retry-After` duration elapses |
| Q4-S1 | A developer changes the payload structure of `SyncPlaylistData` | The contract test fails before the change can be merged |
| Q4-S2 | A developer adds a new business rule to `PlaylistAdapter` | A domain logic test (Layer 1) is added that calls through `PlaylistPort` and verifies the rule using mocked outbound ports |
| Q5-S1 | A developer needs to replace MongoDB with a different database | Only `adapter-out-mongodb` needs to change; domain and all other adapters are unaffected |

# Risks and Technical Debts

## Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Spotify API breaking changes | Medium | High | No versioned Spotify SDK; changes require adapter updates. Monitor Spotify changelog. |
| Spotify API rate limiting | High | Medium | `to-spotify-catalog`/`to-spotify-playlist` partitions throttle at 10s per request; any partition pauses on a 429 until `Retry-After` elapses. |
| Token encryption key loss | Low | High | If `APP_TOKEN_ENCRYPTION_KEY` is lost, all stored tokens are invalid and users must re-authenticate. Key must be backed up securely. |
| MongoDB Atlas outage | Low | High | No local fallback. Application becomes unavailable. MongoDB Atlas provides its own replication and backup. |

## Technical Debts

| Item | Description |
|------|-------------|
| Enrichment completeness | `app_artist`, `app_track`, and `app_album` entries that existed before enrichment was introduced may lack imageLink or albumTitle until re-enriched. |
| Partial-play detection accuracy | Partial play detection relies on polling frequency; very short plays near the end of a track may be missed or misclassified. |
| Test coverage for domain adapters | Domain adapter integration (e.g. `PlaybackService`, `PlaylistService`) is not yet covered by `@QuarkusTest` boundary tests. |

# Glossary

| Term        | Definition                                                                                                            |
|-------------|-----------------------------------------------------------------------------------------------------------------------|
| CDI Event   | Contexts and Dependency Injection event – used for in-process communication between Quarkus beans                    |
| Enrichment  | The process of fetching and storing additional metadata (album details, images) from Spotify for artists, tracks, and albums |
| Outbox      | A persistent task queue used to reliably dispatch Spotify API calls and domain events asynchronously                  |
| Rotating Partition | The `dayOfYear % 14` slice used by `ArtistCatalogSyncJob` to spread the daily catalog resync across ~14 days; unrelated to outbox partitions. |
| Snapshot ID | A Spotify-provided identifier that changes whenever a playlist is modified; used to detect changes efficiently        |
| SSE         | Server-Sent Events – a mechanism for the server to push real-time updates to the browser                              |
| Starter     | A one-time startup bean (`de.chrgroth.quarkus.starters`) that executes arbitrary logic exactly once in production; used for data migrations, schema changes, and one-time bugfixes |
