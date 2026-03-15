# spotify-control

A private Spotify playlist manager for a small, allow-listed set of users.  
It provides smarter playlist management than Spotify itself, personal listening statistics, and automatic playlist maintenance.

## Features

- **Playback Tracking** – polls `recently_played` and `currently_playing` regularly to build a personal listening history
- **Playlist Mirror** – syncs selected Spotify playlists locally, detecting changes via snapshot IDs
- **Automatic Playlist Maintenance** – enforces invariants (no duplicates, album upgrades, yearly union playlists)
- **Listening Statistics** – Spotify Wrapped-style reports available at any time, in more detail

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin · Quarkus · Gradle |
| Frontend | Quarkus Qute (SSR) · htmx · Bootstrap 5 · SSE |
| Database | MongoDB Atlas |
| Deployment | Docker Swarm · Traefik · VPS |
| Monitoring | Grafana Cloud (Prometheus metrics + Loki logs) |

## Quick Start (Local Development)

**Prerequisites:** JDK 21+, a Spotify Developer App, and a MongoDB Atlas cluster.

1. Register a Spotify Developer App and add `http://localhost:8080/oauth/callback` as a redirect URI.
2. Copy the required environment variables into a local `.env` file (see [arc42.md](docs/arc42/arc42.md) — *Deployment View* for the full list).
3. Start the application in dev mode with live reload:

```bash
./gradlew :application-quarkus:quarkusDev
```

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture (arc42)](docs/arc42/arc42.md) | Full architecture documentation |
| [Outbox Pattern](docs/arc42/outbox.md) | Outbox design and usage |
| [Starters](docs/arc42/starters.md) | One-time startup bean infrastructure |
| [ADRs](docs/adr/) | Architecture Decision Records |
| [Release Notes](docs/releasenotes/RELEASENOTES.md) | Version history |
| [Coding Guidelines – Architect](docs/coding-guidelines/role-architect.md) | Architectural conventions |
| [Coding Guidelines – Backend](docs/coding-guidelines/role-backend-developer.md) | Backend coding conventions |
| [Coding Guidelines – Frontend](docs/coding-guidelines/role-frontend-developer.md) | Frontend coding conventions |

## Building & Testing

```bash
# Full build (includes tests and static analysis)
./gradlew build

# Tests only
./gradlew test
```

## License

[MIT](LICENSE)
