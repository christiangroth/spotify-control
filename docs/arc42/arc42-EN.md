---
date: July 2025
title: "![arc42](images/arc42-logo.png) Template"
---

# spotify-control

**About arc42**

arc42, the template for documentation of software and system
architecture.

Template Version 9.0-EN. (based upon AsciiDoc version), July 2025

Created, maintained and © by Dr. Peter Hruschka, Dr. Gernot Starke and
contributors. See <https://arc42.org>.

# Introduction and Goals

## Requirements Overview

spotify-control is a private Spotify playlist manager for a small, allow-listed set of users.
It provides smarter playlist management than Spotify itself, personal listening statistics, and automatic playlist maintenance based on fixed rules.

**Core features (by priority):**

1. **Playback Tracking** – Spotify `recently_played` is polled regularly and stored as raw events. Goal: collect data as early as possible to avoid missing listening history.

2. **Playlist Mirror** – Local copy of relevant Spotify playlists (selected by the user). Each playlist has a type (free-definable enum). Sync via snapshot IDs – a full sync is only performed when a change is detected.

3. **Automatic Playlist Maintenance** – Fixed rules:
   - **All-Invariant:** The `All` playlist is the union of all yearly playlists. It is rarely edited manually, but may contain tracks not present on any other synchronized playlist.
   - **No Duplicates:** Neither on yearly playlists nor on `All`.
   - **Album Upgrade:** A track from a single/EP is replaced by the album version once the album is released. Matching is done via track name + artist. Requires user confirmation (not fully automatic).

4. **Genre Management** – Spotify provides genres only at the artist level. Genres are derived from the artist to tracks (`genre.source = "artist"`). The user can override genres per album/release (`genre.source = "override"`). Overrides may trigger re-enrichment and re-aggregation.

5. **Reports & Listening Statistics** – Spotify Wrapped-style, but available at any time and in more detail. Based on locally collected data. Visualization via MongoDB Charts (embedded) or simple custom visualizations in the frontend using JavaScript.

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

| External System     | Direction      | Description                                                                  |
|---------------------|----------------|------------------------------------------------------------------------------|
| Spotify API         | bidirectional  | Read recently played tracks, playlists, artists; write playlist edits        |
| MongoDB Atlas       | bidirectional  | Persistent storage for all domain data (tracks, playlists, events, etc.)     |
| Slack               | outbound       | Incoming Webhook for notifications and album upgrade approve/reject links     |
| User (browser)      | bidirectional  | Web UI for dashboard, admin, charts, and action endpoints (approve/reject)   |

## Technical Context

| Interface             | Technology                                              |
|-----------------------|---------------------------------------------------------|
| Spotify API           | REST via `adapter-out-spotify`; OAuth 2.0 token refresh |
| MongoDB Atlas         | MongoDB driver via `adapter-out-mongodb`                |
| Slack                 | Incoming Webhook via `adapter-out-slack`                |
| Web UI                | Quarkus Qute SSR, htmx, Bootstrap 5, Server-Sent Events |
| Scheduled jobs        | Quarkus scheduler                                       |
| Internal event bus    | CDI Events (in-process)                                 |
| Async task queue      | Persistent Outbox (`util-outbox`)                       |

# Solution Strategy

- **Hexagonal Architecture** – The application is structured using hexagonal (ports and adapters) architecture to cleanly separate domain logic from infrastructure concerns.
- **Outbox Pattern** – All Spotify API operations are routed through a persistent outbox to ensure reliability and rate limit handling. No direct Spotify calls are made outside `adapter-out-spotify`.
- **Server-Side Rendering** – The frontend uses Quarkus Qute templates with htmx for dynamic interactions, eliminating the need for a separate frontend project, build toolchain, or JavaScript framework.
- **Allow-listed User System** – Access is restricted to users whose Spotify user IDs appear in a configured allow list. Multiple users may be allowed, but no self-service registration exists. This keeps the system private while supporting a small group.
- **Incremental Phases** – The system is built in deployable increments (phases), each providing a complete and useful feature set before moving to the next.

# Building Block View

## Whitebox Overall System

The system is composed of the following Gradle modules:

| Module                  | Role                                                                                  |
|-------------------------|---------------------------------------------------------------------------------------|
| `adapter-in-web`        | REST endpoints, OAuth callback, SSE endpoints, action endpoints (approve/reject)      |
| `adapter-out-mongodb`   | Repository implementations for MongoDB                                                |
| `adapter-out-spotify`   | Spotify API client, token refresh, token bucket rate limiting, backoff                |
| `adapter-out-slack`     | Slack API client, Block Kit message builder                                           |
| `adapter-out-outbox`    | Outbox adapter for writing new tasks into the outbox                                  |
| `application-quarkus`   | Quarkus application bundling and configuration                                        |
| `domain-api`            | Ports (interfaces) – defines the contracts between domain and adapters                |
| `domain-impl`           | Domain services, domain objects, CDI events                                           |
| `util-outbox`           | Outbox implementation (designed to be extractable as a separate external module)      |

### `adapter-in-web`

Handles all inbound HTTP interactions: the web UI (Qute templates), OAuth callback, SSE streams for live updates, and action endpoints for album upgrade approve/reject flows.

### `adapter-out-mongodb`

Implements all repository interfaces defined in `domain-api`. Manages the MongoDB collections for users (including encrypted token storage), tracks, artists, playlists, playback events, aggregations, pending upgrades, and the outbox.

### `adapter-out-spotify`

Encapsulates all communication with the Spotify Web API. Handles token refresh, rate limiting via a token bucket (~50 requests/30s), and backoff for hidden 24h bulk limits.

### `adapter-out-slack`

Sends notifications via a Slack Incoming Webhook, including album upgrade notifications with approve/reject action links.

### `domain-impl`

Contains the core business logic: playback enrichment, aggregation computation, genre derivation and override handling, playlist invariant enforcement, album upgrade matching, and duplicate detection.

### `util-outbox`

A self-contained outbox implementation that routes tasks to domain handlers via partitions. Designed to be potentially extracted as a standalone library.

## Level 2

*work in progress*

# Runtime View

## Album Upgrade Flow

```
Match found (track name + artist, single/EP → album)
    → Entry created in pending_upgrades (UUID, expires_at)
    → Slack notification with approve/reject links
    → Links require authenticated session → redirect to login, then dashboard
    → Dashboard shows all pending_upgrades
    → Action executed → redirect to dashboard with success banner (disappears after 10s)
```

## Playback Polling Flow

```
PlaybackPollJob (every 5 min)
    → writes PollRecentlyPlayed event to outbox (spotify partition)
    → adapter-out-spotify fetches recently_played (max 50 tracks)
    → raw events stored in playback_events_raw (append-only)
    → EnrichPlaybackEvents event written to outbox (domain partition)
    → enrichment service produces playback_events_enriched
```

## Playlist Sync Flow

```
PlaylistCheckJob (every 15 min)
    → GET /v1/me/playlists → fetch all snapshot IDs in one request
    → compare with local snapshot IDs
    → for each changed playlist: write SyncPlaylist event to outbox
    → full sync of changed playlists (tracks, artists, albums)
```

# Deployment View

## Infrastructure Level 1

The application is deployed on an existing VPS running Docker Swarm. Traefik handles routing, TLS termination, and HTTPS. MongoDB is hosted externally on MongoDB Atlas.

| Component       | Technology              | Notes                                      |
|-----------------|-------------------------|--------------------------------------------|
| Application     | Quarkus (native Docker) | Deployed as a Docker Swarm service         |
| Reverse Proxy   | Traefik                 | TLS via Let's Encrypt, already provisioned |
| Database        | MongoDB Atlas           | Two projects: prod + dev                   |
| Notifications   | Slack Incoming Webhook  | External service                           |

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
- **Releasenotes plugin** – custom Gradle plugin implemented in `buildSrc` (`de.chrgroth.gradle.plugins.releasenotes`); auto-registers its tasks and integrates with the release plugin; on release it collects snippets, generates a new section in `docs/releasenotes/RELEASENOTES.md`, copies it back to sources, and deletes the consumed snippets – all committed as part of the release
- **CI/CD** – the GitHub Actions workflow (`gradle.yml`) runs `./gradlew build` on every push; runs `./gradlew release` only on pushes to `main`; after release, the Docker stack file is copied to the VPS via SCP and the stack is deployed via SSH
- **Snippet requirement** – every branch that is not `main` or `dependabot/*` **must** contain at least one release note snippet in `docs/releasenotes/releasenotes-snippets/`; the build fails without it. Create snippets with the corresponding Gradle tasks (`releasenotesCreateFeature`, `releasenotesCreateBugfix`, …); filenames follow the pattern `{branch-last-segment}-{type}.md`

### Spotify OAuth Redirect URIs

Both URIs must be registered in the Spotify Developer App (replace `spotify.yourdomain.com` with the actual production domain):

```
https://spotify.yourdomain.com/oauth/callback   ← Production
http://localhost:8080/oauth/callback             ← Local development
```

# Cross-cutting Concepts

## Authentication and Access Control

- Spotify OAuth 2.0 Authorization Code Flow.
- In the OAuth callback: the Spotify user ID is checked against `APP_ALLOWED_SPOTIFY_USER_IDS` (environment variable, comma-separated list). If the ID is not present in the list, the session is invalidated and nothing is persisted.
- A `User` document is upserted in the `users` MongoDB collection only after a successful allow-list check. Both access and refresh tokens are stored encrypted (AES-256-GCM) using `APP_TOKEN_ENCRYPTION_KEY`.
- Session-based authentication for all endpoints, including action endpoints. The session stores only the Spotify user ID – never tokens.
- `return_to` parameter stored in the session for redirect after login.
- A CSRF `state` parameter is generated per authorization request and validated in the callback.
- Token refresh is handled by `adapter-out-spotify` before each Spotify API call; the refreshed token is persisted back to MongoDB.

## Error Handling

All domain failures are represented as typed `DomainError` values wrapped in Arrow's `Either<DomainError, T>`. This replaces ad-hoc sealed result classes and uncaught exceptions.

- Port interfaces return `Either<DomainError, T>` instead of raw domain objects or throwing exceptions.
- Infrastructure adapters (`adapter-out-*`) catch all exceptions at the adapter boundary and convert them to typed `Either.Left<DomainError>` values – no exceptions cross port boundaries.
- Domain services compose multiple fallible operations using the Arrow `either { }` DSL with `bind()`.
- Web adapters translate `Either.Left<DomainError>` to HTTP error responses (redirect with `?error=<code>`).
- Error codes follow the convention `<AREA>-<NNN>` (e.g. `AUTH-001`). Codes are stable once published.

**Error code registry:**

| Prefix  | Domain Area            | Example codes                          |
|---------|------------------------|----------------------------------------|
| `AUTH`  | Authentication / login | AUTH-001, AUTH-002, AUTH-003, AUTH-004 |
| `TOKEN` | Token en/decryption    | TOKEN-001, TOKEN-002, TOKEN-003        |

See [ADR-0006](../adr/0006-error-handling-concept.md) for the full design rationale.

## Outbox Pattern

All Spotify API operations and domain-level async tasks are routed through a persistent outbox. This ensures reliability and decouples producers from consumers.

**Partitions and event types:**

| Partition | Event Types                                                                                                                                |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `spotify` | `SyncPlaylist`, `SyncTrack`, `SyncArtist`, `PushPlaylistEdit`, `PollRecentlyPlayed`                                                        |
| `domain`  | `EnrichPlaybackEvents`, `RecomputeAggregations`, `ApplyGenreOverride`, `SyncPlaylistInvariant`, `CheckAlbumUpgrades`, `ApplyAlbumUpgrade`  |

Successfully processed events are moved to `outbox_archive` (audit log). Internal triggers between services use CDI events (not the outbox).

## Server-Sent Events (SSE) and Live Updates

CDI events act as a bridge between backend services and SSE streams. The SSE endpoint delivers the initial state on connect, then pushes updates via a hot stream backed by a `BroadcastProcessor`.

## Genre Management

Spotify provides genres only at the artist level. Genres are derived from the artist to individual tracks (`genre.source = "artist"`). The user can override genres per album/release (`genre.source = "override"`). Overrides trigger re-enrichment and re-aggregation as needed. The `enrichment_version` field tracks which version of enrichment logic was applied.

## Scheduler Jobs

| Job                | Interval        | Outbox Event                               |
|--------------------|-----------------|--------------------------------------------|
| `PlaybackPollJob`  | every 5 min     | `PollRecentlyPlayed`                       |
| `PlaylistCheckJob` | every 15 min    | `SyncPlaylist` (only on snapshot change)   |
| `AggregationJob`   | nightly         | `RecomputeAggregations`                    |

## MongoDB Charts Contract

Charts work exclusively on `aggregations_*` collections. Field names are stable (public API) – breaking changes require contract test updates. `@QuarkusTest` contract tests validate the schema on every build. Additive changes (new fields) are non-breaking.

## Frontend Approach

No separate frontend project. The UI is rendered server-side using Quarkus Qute templates. Dynamic interactions are handled by htmx. No React, Vue, npm, Node.js, or build steps are required.

**Technology stack:**
- Templates: Qute (Quarkus SSR)
- CSS: Bootstrap 5 via WebJar
- Interactivity: htmx via WebJar
- Icons: Font Awesome via WebJar
- Charts: MongoDB Charts Embedding SDK
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
SLACK_WEBHOOK_URL
```

# Architecture Decisions

Architecture decisions are documented as Architecture Decision Records (ADRs) in the [`/docs/adr`](../adr/) folder.

| ADR | Title |
|-----|-------|
| [0001](../adr/0001-using-arc42-as-project-documentation.md) | Using arc42 as Project Documentation |
| [0002](../adr/0002-backend-hexagonal-architecture.md) | Backend: Hexagonal Architecture |
| [0003](../adr/0003-no-separate-frontend-project.md) | No Separate Frontend Project |
| [0004](../adr/0004-using-ai-coding-agents.md) | Using AI Coding Agents |
| [0005](../adr/0005-markdown-rendering-library.md) | Markdown Rendering Library: marked |

# Quality Requirements

## Quality Requirements Overview

*work in progress*

## Quality Scenarios

*work in progress*

# Risks and Technical Debts

*work in progress*

# Glossary

| Term                    | Definition                                                                                                            |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Outbox                  | A persistent task queue used to reliably dispatch Spotify API calls and domain events asynchronously                  |
| Snapshot ID             | A Spotify-provided identifier that changes whenever a playlist is modified; used to detect changes efficiently        |
| All-Invariant           | The rule that the `All` playlist must always be the union of all yearly playlists                                     |
| Album Upgrade           | The process of replacing a track from a single/EP with the equivalent track from the full album release               |
| Pending Upgrade         | A proposed album upgrade waiting for user confirmation (approve or reject)                                            |
| Enrichment              | The process of deriving additional metadata (genres, skip detection) from raw playback events                         |
| Enrichment Version      | A version counter on enriched events that tracks which enrichment logic version was applied                           |
| Genre Override          | A user-defined genre assignment for an album/release that takes precedence over artist-derived genres                 |
| Aggregation             | Pre-computed monthly listening statistics stored in `aggregations_monthly` for use by MongoDB Charts                  |
| SSE                     | Server-Sent Events – a mechanism for the server to push real-time updates to the browser                              |
| Token Bucket            | A rate limiting mechanism used to throttle Spotify API requests (~50 requests per 30 seconds)                        |
| CDI Event               | Contexts and Dependency Injection event – used for in-process communication between Quarkus beans                    |
