# spotify-control

# Introduction and Goals

## Requirements Overview

spotify-control is a private Spotify playlist manager for a small, allow-listed set of users.

**Implemented features:**

1. **Playback Tracking** – Spotify `recently_played` and `currently_playing` are polled regularly and stored. Partial plays (tracks not played to completion) are detected via observation sessions and stored separately.

2. **Playlist Mirror** – Local copy of selected Spotify playlists. Sync is driven by snapshot IDs – a full track sync is only performed when Spotify reports a change.

3. **Catalog Sync** – Artist images, track album references, and album details (title, cover) are fetched from the Spotify API and stored in deduplicated `app_artist`, `app_track`, and `app_album` collections.

4. **Listening Statistics** – Playback data is aggregated into a per-user dashboard showing total play counts, daily play trends, top artists, top tracks, and recently played items.

5. **Artist Playback Filtering** – Users can mark artists as `ACTIVE`, `INACTIVE`, or `UNDECIDED`. Tracks from `INACTIVE` artists are excluded from playback processing.

## Quality Goals

*work in progress*

## Stakeholders

| Role/Name         | Contact     | Expectations                                                              |
|-------------------|-------------|---------------------------------------------------------------------------|
| Developer / User  | (private)   | Allow-listed user(s); operates and uses the application for personal use  |

# Architecture Constraints

- **Allow-listed users** – Multiple users are supported, but access is restricted to a configured allow list of Spotify user IDs. No registration, no user-management UI.
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
| Spotify API         | bidirectional  | Read recently played tracks, playlists, artists; OAuth 2.0 login        |
| MongoDB Atlas       | bidirectional  | Persistent storage for all domain data (tracks, playlists, events, etc.) |
| User (browser)      | bidirectional  | Web UI for dashboard, settings, and documentation                        |
| Slack               | outbound       | System notifications via incoming webhook (startup, shutdown, outbox partition events) |

## Technical Context

| Interface             | Technology                                              |
|-----------------------|---------------------------------------------------------|
| Spotify API           | REST via `adapter-out-spotify`; OAuth 2.0 token refresh |
| MongoDB Atlas         | MongoDB driver via `adapter-out-mongodb`                |
| Web UI                | Quarkus Qute SSR, htmx, Bootstrap 5, Server-Sent Events |
| Scheduled jobs        | Quarkus scheduler                                       |
| Internal event bus    | CDI Events (in-process)                                 |
| Async task queue      | Persistent Outbox (`de.chrgroth.quarkus.outbox`)        |
| Slack                 | REST POST via `adapter-out-slack`; incoming webhook     |

# Solution Strategy

- **Hexagonal Architecture** – The application is structured using hexagonal (ports and adapters) architecture to cleanly separate domain logic from infrastructure concerns.
- **Outbox Pattern** – All Spotify API operations are routed through a persistent outbox to ensure reliability and rate limit handling. No direct Spotify calls are made outside `adapter-out-spotify`.
- **Server-Side Rendering** – The frontend uses Quarkus Qute templates with htmx for dynamic interactions, eliminating the need for a separate frontend project or JavaScript framework.
- **Allow-listed User System** – Access is restricted to users whose Spotify user IDs appear in a configured allow list. Multiple users are supported, but no self-service registration exists.

# Building Block View

## Whitebox Overall System

The system is composed of the following Gradle modules:

| Module                  | Role                                                                              |
|-------------------------|-----------------------------------------------------------------------------------|
| `adapter-in-outbox`     | Outbox event dispatcher – routes outbox events to the correct domain port handler |
| `adapter-in-scheduler`  | Scheduled jobs for polling Spotify and syncing data                               |
| `adapter-in-starter`    | One-time startup bean implementations for data migrations and bugfixes            |
| `adapter-in-web`        | REST endpoints, OAuth callback, SSE endpoints, action endpoints                   |
| `adapter-out-mongodb`   | Repository implementations for MongoDB                                            |
| `adapter-out-outbox`    | Outbox adapter for writing new tasks into the outbox                              |
| `adapter-out-scheduler` | Scheduler info provider for the health page                                       |
| `adapter-out-slack`     | Slack notification adapter for system notifications via incoming webhook          |
| `adapter-out-spotify`   | Spotify API client, token refresh, token bucket rate limiting, backoff            |
| `application-quarkus`   | Quarkus application bundling and configuration                                    |
| `domain-api`            | Ports (interfaces) – defines the contracts between domain and adapters            |
| `domain-impl`           | Domain services and business logic                                                |

![Module overview](https://kroki.io/plantuml/svg/bVPBasMwDL37K0TuYWznMdqsDHoYG_Sww-hBid3U1LaC7dB1o_8-x05bl-QQLD29KO9JzsJ5tL7XirmDNB1a1NCQ7sgI4zf-pARY0Xg0rRIZRZOhZm9JC_C2zytuj5yO0rSwQ-XyChc77JV_o9BY_gp4fJr7JvwxgAqbQ2upN_yVFFn42ksvBpwsFzZhlQokdmYMGx_SorJ0dMIWgA7qFLNGUc-h2HTk5e4Ey891LLuUYycZR481OgHFO5mWVhUsvUIXaZrXjF3dQzG-VjbBgSVVRKlZfW3qQXLCAb6RY-eFLaUpqfc1_WyHrtKkZMJxzV7wXgkbaTGbcoZtXRgpnnCOoo71cIba-V7jijRKc5XIY1qGScADjInUnYoNUj7t8THonzMajJV6GCNPCmI8pWTDCOHcNAbWzDgCPMNLW9nmi-VdVB0ux3gToCxf4kDCE-PRW2x7B6Sh5tBlYTnG0hmh5DIDbqYy8Gogx25yWewCz7FhuHe3UsKyK7sQhocf9h8)

### `adapter-in-outbox`

Dispatches outbox events to the appropriate domain port handler. Implements `OutboxTaskDispatcher` – receives a deserialized `DomainOutboxEvent` and calls the correct `handle(event)` method on the domain port.

### `adapter-in-scheduler`

Contains Quarkus `@Scheduled` jobs that trigger domain actions at configured intervals. All jobs skip execution via `skipExecutionIf = StarterSkipPredicate::class` until all starters have completed.

### `adapter-in-starter`

Contains concrete `Starter` implementations acting as inbound adapters: they receive a startup trigger from `de.chrgroth.quarkus.starters` and call into the domain via port interfaces. Each starter executes exactly once in production mode. Used for one-time data migrations, schema changes, and bugfixes.

### `adapter-in-web`

Handles all inbound HTTP interactions: the web UI (Qute templates), OAuth callback, SSE streams for live updates, and settings action endpoints.

### `adapter-out-mongodb`

Implements all repository interfaces defined in `domain-api`. Manages the MongoDB collections for users (including encrypted token storage), tracks, artists, albums, playlists, and playback events.

#### MongoDB Collections

| Collection                        | Description                                                                                                                     |
|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `app_album`                       | Deduplicated album metadata: title, cover image, main artist reference, lastSync.                             |
| `app_artist`                      | Deduplicated artist metadata: name, imageLink, lastSync, playbackProcessingStatus (UNDECIDED/ACTIVE/INACTIVE). |
| `app_playback`                    | Processed playback events combining recently played and partial played data.                                                    |
| `app_sync_pool`                   | Pending sync entries: Spotify IDs of artists, tracks, and albums awaiting bulk sync from the Spotify API.               |
| `app_track`                       | Deduplicated track metadata: title, main artist reference, additional artist references, album reference, lastSync.   |
| `app_user`                        | Spotify user profile with encrypted access and refresh tokens.                                                                  |
| `outbox`                          | Persistent outbox task queue (managed by `de.chrgroth.quarkus.outbox`).                                                        |
| `outbox_archive`                  | Archived completed/failed outbox tasks (managed by `de.chrgroth.quarkus.outbox`).                                              |
| `spotify_currently_playing`       | Currently playing track observations per user.                                                                                  |
| `spotify_playlist`                | Full playlist data including all tracks.                                                                                        |
| `spotify_playlist_metadata`       | Playlist metadata: name, snapshot ID, sync status.                                                                              |
| `spotify_recently_partial_played` | Partial play events (plays that did not complete a full track).                                                                 |
| `spotify_recently_played`         | Raw recently played track events (append-only).                                                                                 |
| `starters`                        | One-time startup bean execution state (managed by `de.chrgroth.quarkus.starters`).                                             |

### `adapter-out-outbox`

Implements `OutboxPort` and `OutboxManagementPort`. Bridges the domain to the `de.chrgroth.quarkus.outbox` library for writing and managing outbox tasks.

### `adapter-out-scheduler`

Implements `CronjobInfoPort`. Provides scheduled job metadata (name, next execution, running state) to the health page via the Quarkus `Scheduler` API.

### `adapter-out-slack`

Sends system notifications to a configured Slack incoming webhook. Observes Quarkus `StartupEvent` and `ShutdownEvent` lifecycle events and implements `OutboxPartitionObserver` to react to partition pause/resume events. Each notification type is individually enabled via configuration properties. The webhook URL is sensitive and must be set via the `SLACK_WEBHOOK_URL` environment variable in production.

Two categories of notifications are supported:

- **System notifications** – lifecycle and infrastructure events. Notification toggles are configured via application properties; the webhook URL is set via the `SLACK_WEBHOOK_URL` environment variable in production.
- **User notifications** – user-facing alerts configured through the UI (not yet implemented).

### `adapter-out-spotify`

Encapsulates all communication with the Spotify Web API. Handles token refresh, rate limiting (10s throttle on the `to-spotify` partition), and backoff for hidden 24h bulk limits. Implements bulk fetch endpoints (`GET /v1/artists?ids=`, `GET /v1/tracks?ids=`, `GET /v1/albums/{id}`) with per-item fallback for the direct track endpoint.

### `application-quarkus`

Bundles all modules into the runnable Quarkus application. Contains test infrastructure and integration tests (`@QuarkusTest`).

### `domain-api`

Defines all port interfaces (`port.in.*`, `port.out.*`), domain models, outbox event types (`DomainOutboxEvent`), and outbox partitions (`DomainOutboxPartition`).

### `domain-impl`

Contains the core business logic: playback data processing, playlist synchronization, artist catalog management, user profile handling, dashboard statistics computation, and token encryption.

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

*work in progress*

# Runtime View

## Playback Polling Flow

![Playback polling sequence](https://kroki.io/plantuml/svg/jVTLbtswELzzKxY-2UgNRz0GcBEnToociqR17gJNriPCEsny4UT9-q4oyZBStc1N1MzOzC5XuvaBuxCrkvmj0pY7XkFltBGFMxVCcBEHiC-4NK9Kv8CBl36ISDzwWIZ7o8NO_ULIsmEZ_oyoBW6cM6_PhRJHjd5DxhjhQQlluQ4w-x65O0bPdqJAGUt0M-AefHMaE59KXu-5OG4kt6GjWT7mPMawN29s_s3oF7O9WSSSaV-OiDtrgjrUsHl6aP3aM5M88D33CLNOIqFV88zYeg13J3Q1fL6k7oTR0sN6zVJWWH6hNHAFqKntiPcYRHEbnUMdyrrJTgOcLyhFw2wjEXuSBnOLDqJHtxiYZpdQKR0D_sf0B4qzGMq_WI5J7w2DWXYDWdpu6pDGF5TRjXsn1tsXXMsS55PN9P6dILG_3j3D6pStKlw16ug6Rpoy4dFSkNAX5KIXzG2ryP5lP27sA-Yr11WkVtPSDcNwa1HLc5ienE-SaSVOTfQ0K_pWBtaDyhYcK5xvp7tJ2CTffue3tJbpZqSpuNIfuYw_BRbTc6YO80bOh0_pOTjiT0-hgfuFeM-QBCcVuIAkAQ9bT5uUinytRW6NKdk16dCP5zc)

```
CurrentlyPlayingFetchJob (every 20s)
    → enqueue FetchCurrentlyPlaying (to-spotify-playback partition, per user)
    → Spotify GET /v1/me/player → stored in spotify_currently_playing

RecentlyPlayedFetchJob (every 10min)
    → enqueue FetchRecentlyPlayed (to-spotify-playback partition, per user)
    → Spotify GET /v1/me/player/recently-played → new items stored in spotify_recently_played
    → convert partial plays → new items stored in spotify_recently_partial_played
    → if any new data: enqueue AppendPlaybackData (domain partition)
```

## Currently Playing → Partial Play Conversion

The `spotify_currently_playing` collection accumulates observations of what the user is listening to right now. After each `FetchRecentlyPlayed`, observations are grouped into contiguous play sessions per track. A session is convertible when:

- The track does not appear in the latest `recently_played` response (i.e. it was not played to completion).
- It is not the most recently observed non-completed session (which may still be active).
- The maximum observed progress exceeds the configured minimum (default: 25 seconds).

For each convertible session, a `RecentlyPartialPlayedItem` is created with the observed play duration and written to `spotify_recently_partial_played`. Converted and completed track observations are then deleted from `spotify_currently_playing`.

## Playback Data Processing Flow

Raw playback data from `spotify_recently_played` and `spotify_recently_partial_played` is processed into the normalised `app_*` collections by `PlaybackDataAdapter`. There are two modes:

**Append (triggered automatically):** After new raw data arrives, `AppendPlaybackData` is enqueued on the `domain` partition. The adapter first loads all artists with `INACTIVE` playback processing status, then filters raw playback items to skip tracks whose primary artist is inactive. For remaining items it fetches all source items newer than the most recent `app_playback` entry for the user, deduplicates against existing `app_playback` timestamps, then:
1. Upserts artist metadata into `app_artist` (artistId, artistName) — enriched imageLink is preserved on re-encounter.
2. Upserts track metadata into `app_track` (trackId, trackTitle, artistId, additionalArtistIds) — albumId is preserved if already enriched.
3. Appends new entries to `app_playback` (userId, playedAt, trackId, secondsPlayed). The document `_id` is a composite of `${userId}:${playedAt.toEpochMilli()}:${trackId}` for natural deduplication.
4. Adds artist IDs to `app_sync_pool` and track IDs to `app_sync_pool` for later bulk sync.

**Catalog Sync (bulk-scheduled, `to-spotify` partition):**
- `SyncMissingArtists`: bulk-syncs up to 50 pending artist IDs from `app_sync_pool` via `GET /v1/artists?ids=`. Updates `app_artist` with imageLink. Runs every 10 minutes (at :00, :10, …).
- `SyncMissingTracks`: bulk-syncs up to 50 pending track IDs from `app_sync_pool`. For tracks with a known `albumId`, fetches all tracks for that album via `GET /v1/albums/{id}` (all album tracks are stored, not only the requested subset). Tracks without a known `albumId` or not found in the album response fall back to `GET /v1/tracks?ids=`. After syncing, discovered album and track artist IDs are added to `app_sync_pool` for further sync. Runs every 10 minutes (at :05, :15, …).

**Per-item sync (on-demand, `to-spotify` partition):**
- `SyncArtistDetails(artistId, userId)`: skipped if already synced; otherwise calls `GET /v1/artists/{id}` and updates `app_artist` with imageLink.
- `SyncTrackDetails(trackId, userId)`: skipped if already synced; otherwise calls `GET /v1/tracks/{id}`, updates `app_track` sync fields and album reference, upserts `app_album`, and enqueues `SyncArtistDetails` for all track artists.

**Album sync (bulk-scheduled, `to-spotify` partition):**
- `SyncMissingAlbums`: bulk-syncs up to 10 pending album IDs from `app_sync_pool` via `GET /v1/albums/{id}`. Upserts ALL returned tracks and album metadata. Track artist IDs are added to `app_sync_pool` for artist sync. Albums are added to the pool when discovered during direct track sync (`SyncMissingTracks`) or via `resyncCatalog`. Runs every 10 minutes (at :08, :18, …).

**Rebuild (user-triggered from Settings):** Deletes all `app_playback` entries for the user and re-runs the Append logic from scratch over all source data.

## Artist Playback Processing

The Settings page allows users to control which artists are included in playback processing via three statuses stored on `app_artist.playbackProcessingStatus`:

| Status      | Description                                                                                               |
|-------------|-----------------------------------------------------------------------------------------------------------|
| `UNDECIDED` | Default for newly discovered artists. Treated identically to `ACTIVE` during processing.                 |
| `ACTIVE`    | Artist tracks are included in playback processing.                                                       |
| `INACTIVE`  | Artist tracks are excluded. All existing `app_playback` entries for the artist's tracks are deleted on transition to this status. |

When an artist is set from `INACTIVE` back to `ACTIVE` or `UNDECIDED`, a `RebuildPlaybackData` event is enqueued for all users so that previously excluded playback records are recreated.

## Playlist Sync Flow

![Playlist sync sequence](https://kroki.io/plantuml/svg/hVNLj9MwEL77V4x6SgWrqNeViraoPHpALOpyrqbxtLHqeIw9ZgmI_47zWrUFdm-xv2--x1i5i4JBUmNVPBnnMWADDTuu6sANgYREZ0isUfOjcUc4oI3niKYDJivv2cnW_CRYLM7H6FsiV9EqBH58qE11chQjLJTKuJjKeHQCsy8JwylFta1q0slSmAFGiN3pknhvsbUmykqjl5Hm8ZLzOcmef6jiE7sjr9_OexIPlxfErWcxhxZW95vBbzgrjYJ7jASzUaJHm-5bqeUSPnIKtoXlUvUJ4eZNzgC3QC6XTfTVZwGKxTy7ddhgnfFt66qpwMYdGApPAVKkMO91hW_GCNDnFMOucxkFJpsanbZUXKtNdpPELXx49wDl90XZUOlHYhxJfZlMqbjJVgTRoY81C2zWUT2Xe513A0WVMxxz8ydZYGfb3OLlqJ3A_6M-CZa_jP5dSsDqdJ05-bwxmYZ308jrv252DQl2j_lvAfR-1625G-2-ezOIkvbXjqg1DEx4BQMt7ym_Vz8Xc7udZ7bqjpzOP9Qf)

```
PlaylistSyncJob (hourly at :30)
    → enqueue SyncPlaylistInfo (to-spotify partition, per user)
    → Spotify GET /v1/me/playlists → compare snapshot IDs
    → for each changed playlist: enqueue SyncPlaylistData

SyncPlaylistData (to-spotify partition)
    → Spotify GET /v1/playlists/{id}/tracks
    → upsert spotify_playlist, spotify_playlist_metadata
    → upsert app_artist, app_track stubs
    → add artist and track IDs to app_sync_pool for bulk catalog sync
```

## Catalog Sync Flow

![Catalog sync sequence](https://kroki.io/plantuml/svg/vVVNb9swDL37VxA5OdiKxIcCQwBvzdZuyKHYhuReKBadCJZFTR_JsmH_fbJlo0mWrB8oepPIx8fHRxm-so4Z52uZ2EoozQyroSZFxdpQjeCMx72MXTNOW6FWUDJp9zMcS-al-0zKzcUvhCzbL8MfHlWBU2Nou1iLolJoLWRJEvJOFEIz5WDw3TNTeZvMizVyL9EMgFmwze0Q-Ik5Jmk15Uy7DlWwQ8hX75b0M0lvSa3o-uOwBVEMHgDnmpwodzD9Novt4j3hoceSWYRBR9Fm6-acJHkONxs0O8jGUAvlHVpIJ-PxEPI8aQXDxfugCSZgvEqHSTiGQOwfgvOdKm6FtcHJadBinT3LefkMzoVhRXWe8t1zZMqlryOlo4vOJGiddIJUQ9iV9YxrprjE9N9R-zatlwGoEasulV4GC8vw8oBpfWdD6Z0mkl1B33UCX24WMNpkIxbLPghu8yNWry0a1_JEFKQrVAbtWxA1W-GxCoM1bRCansGW2bU9pePhGaP1p0aMmf9P2OMlUeU1sMb0Gd-T4hqS83a0Sxr9FvwPpBoNVIq2qqcZnnFIyntqeBMdayqO4Ixz6Jxs3HH0yB21vHFFkCrq1cAIFDkoyauzyh6rqok3mAfVvfCy40dx8j23mTR74nO-399r7OpJblyh4uE38Rc)

```
SyncMissingArtistsJob (every 10 minutes at :00)
    → enqueue SyncMissingArtists (to-spotify partition)
    → peekArtists(50) from app_sync_pool
    → Spotify GET /v1/artists?ids=
    → upsert app_artist (imageLink)
    → remove synced IDs from app_sync_pool

SyncMissingTracksJob (every 10 minutes at :05)
    → enqueue SyncMissingTracks (to-spotify partition)
    → peekTracks(50) from app_sync_pool
    → lookup albumIds from app_track for grouped tracks:
        ├─ known albumId → GET /v1/albums/{id} (all album tracks upserted)
        │    → add artist IDs to app_sync_pool
        │    → remove album ID from app_sync_pool (if present)
        └─ no albumId / not found in album → GET /v1/tracks?ids=
             → upsert app_track + app_album
             → add discovered album + artist IDs to app_sync_pool
    → remove synced track IDs from app_sync_pool

SyncMissingAlbumsJob (every 10 minutes at :08)
    → enqueue SyncMissingAlbums (to-spotify partition)
    → peekAlbums(10) from app_sync_pool
    → GET /v1/albums/{id} per album
    → upsert all app_track + app_album
    → add artist IDs to app_sync_pool
    → remove synced album IDs from app_sync_pool
```

# Deployment View

## Infrastructure Level 1

The application is deployed on an existing VPS running Docker Swarm. Traefik handles routing, TLS termination, and HTTPS. MongoDB is hosted externally on MongoDB Atlas.

| Component       | Technology              | Notes                                      |
|-----------------|-------------------------|--------------------------------------------|
| Application     | Quarkus (native Docker) | Deployed as a Docker Swarm service         |
| Reverse Proxy   | Traefik                 | TLS via Let's Encrypt, already provisioned |
| Database        | MongoDB Atlas           | Two projects: prod + dev                   |

## Infrastructure Level 2

Secrets are never stored in deployment configuration – always provided via environment variables from a `.env` file that is not checked into Git.

### Environments

|                     | Local                          | Production                |
|---------------------|--------------------------------|---------------------------|
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
- **CI/CD** – the GitHub Actions workflow (`gradle.yml`) runs `./gradlew build` on every push; runs `./gradlew release` only on pushes to `main`; after release, the Docker stack file is copied to the VPS via SCP and the stack is deployed via SSH
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
- In the OAuth callback: the Spotify user ID is checked against `APP_ALLOWED_SPOTIFY_USER_IDS` (environment variable, comma-separated list). If the ID is not present in the list, the session is invalidated and nothing is persisted.
- A `User` document is upserted in the `app_user` MongoDB collection only after a successful allow-list check. Both access and refresh tokens are stored encrypted (AES-256-GCM) using `APP_TOKEN_ENCRYPTION_KEY`.
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

| Partition             | Throttle | Rate limit pause | Event Types                                                                                            |
|-----------------------|----------|------------------|--------------------------------------------------------------------------------------------------------|
| `to-spotify`          | 10s      | yes              | `UpdateUserProfile`, `SyncPlaylistInfo`, `SyncPlaylistData`, `SyncArtistDetails`, `SyncTrackDetails`, `SyncMissingArtists`, `SyncMissingTracks`, `SyncMissingAlbums`, `ResyncCatalog` |
| `to-spotify-playback` | none     | no               | `FetchCurrentlyPlaying`, `FetchRecentlyPlayed`                                                         |
| `domain`              | none     | no               | `RebuildPlaybackData`, `AppendPlaybackData`                                                             |

Successfully processed events are moved to `outbox_archive` (audit log). Internal triggers between services use CDI events (not the outbox).

## Server-Sent Events (SSE) and Live Updates

Backend services notify SSE streams via CDI events. The SSE endpoint delivers the initial state on connect, then pushes named update events to connected clients via per-user reactive streams.

## Scheduler Jobs

| Job                          | Interval                      | Outbox Event(s)                                                       |
|------------------------------|-------------------------------|-----------------------------------------------------------------------|
| `CurrentlyPlayingFetchJob`   | every 20 seconds              | `FetchCurrentlyPlaying` (per user)                                    |
| `RecentlyPlayedFetchJob`     | every 10 minutes              | `FetchRecentlyPlayed` (per user) → auto-enqueues `AppendPlaybackData` |
| `PlaylistSyncJob`            | hourly (at :30)               | `SyncPlaylistInfo` (per user)                                         |
| `SyncMissingArtistsJob`      | every 10 minutes (at :00)     | `SyncMissingArtists`                                                  |
| `SyncMissingTracksJob`       | every 10 minutes (at :05)     | `SyncMissingTracks`                                                   |
| `SyncMissingAlbumsJob`       | every 10 minutes (at :08)     | `SyncMissingAlbums`                                                   |
| `UserProfileUpdateJob`       | daily at 04:00                | `UpdateUserProfile` (per user)                                        |

All scheduler jobs skip execution via `skipExecutionIf = StarterSkipPredicate::class` until all starters have completed successfully.

## Starters

One-time startup beans for data migrations, schema changes, and one-time bugfixes. Each starter executes exactly once in `NORMAL` (prod) mode; failed starters are retried on the next application start. The Quarkus scheduler is blocked until all starters succeed.

## Frontend Approach

No separate frontend project. The UI is rendered server-side using Quarkus Qute templates. Dynamic interactions are handled by htmx. No React, Vue, npm, Node.js, or build steps are required.

**Technology stack:**
- Templates: Qute (Quarkus SSR)
- CSS: Bootstrap 5 via WebJar
- Interactivity: htmx via WebJar
- Icons: Font Awesome via WebJar
- Live Updates: Server-Sent Events via htmx `hx-ext="sse"`
- Markdown rendering: marked via WebJar (docs and release notes pages)

## Documentation and Release Notes Serving

Architecture documentation (`docs/arc42`), ADRs (`docs/adr`), and release notes (`docs/releasenotes`) are served to the logged-in user directly from the application. A Gradle copy task bundles the Markdown files into the `adapter-in-web` classpath at build time. A `DocsResource` endpoint reads and passes the raw Markdown to Qute templates; the `marked` WebJar renders it in the browser.

## Configuration

All sensitive configuration is provided via environment variables:

```
APP_ALLOWED_SPOTIFY_USER_IDS
SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
MONGODB_CONNECTION_STRING
APP_TOKEN_ENCRYPTION_KEY
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

# Quality Requirements

## Quality Requirements Overview

*work in progress*

## Quality Scenarios

*work in progress*

# Risks and Technical Debts

## Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Spotify API breaking changes | Medium | High | No versioned Spotify SDK; changes require adapter updates. Monitor Spotify changelog. |
| Spotify API rate limiting | High | Medium | `to-spotify` partition throttles at 10s per request. `pauseOnRateLimit` stops the partition on 429 responses. |
| Token encryption key loss | Low | High | If `APP_TOKEN_ENCRYPTION_KEY` is lost, all stored tokens are invalid and users must re-authenticate. Key must be backed up securely. |
| MongoDB Atlas outage | Low | High | No local fallback. Application becomes unavailable. MongoDB Atlas provides its own replication and backup. |

## Technical Debts

| Item | Description |
|------|-------------|
| `HelloWorldStarter` | Demo starter with no production purpose; can be removed. |
| Enrichment completeness | `app_artist`, `app_track`, and `app_album` entries that existed before enrichment was introduced may lack imageLink or albumTitle until re-enriched. |
| Partial-play detection accuracy | Partial play detection relies on polling frequency; very short plays near the end of a track may be missed or misclassified. |
| Test coverage for domain adapters | Domain adapter integration (e.g. `PlaybackDataAdapter`, `PlaylistSyncAdapter`) is not yet covered by `@QuarkusTest` boundary tests. |

# Glossary

| Term        | Definition                                                                                                            |
|-------------|-----------------------------------------------------------------------------------------------------------------------|
| CDI Event   | Contexts and Dependency Injection event – used for in-process communication between Quarkus beans                    |
| Enrichment  | The process of fetching and storing additional metadata (album details, images) from Spotify for artists, tracks, and albums |
| Outbox      | A persistent task queue used to reliably dispatch Spotify API calls and domain events asynchronously                  |
| Snapshot ID | A Spotify-provided identifier that changes whenever a playlist is modified; used to detect changes efficiently        |
| SSE         | Server-Sent Events – a mechanism for the server to push real-time updates to the browser                              |
| Starter     | A one-time startup bean (`de.chrgroth.quarkus.starters`) that executes arbitrary logic exactly once in production; used for data migrations, schema changes, and one-time bugfixes |
| Token Bucket | A rate limiting mechanism used to throttle Spotify API requests                                                      |
