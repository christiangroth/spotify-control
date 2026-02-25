# Copilot Instructions for spotify-control

## Project Overview

**spotify-control** is a personal Spotify playlist manager for a single user. It provides:

- **Playback Tracking:** Regular polling of Spotify's recently played tracks
- **Playlist Mirror:** Local sync of Spotify playlists via snapshot IDs
- **Automatic Maintenance:** Deduplication, album upgrades, all-playlist unions
- **Genre Management:** Artist-derived genres and user overrides
- **Statistics & Reporting:** Spotify Wrapped-style analytics

## Technology Stack

- **Language:** Kotlin (idiomatic Kotlin — avoid Java idioms)
- **Framework:** Quarkus (JVM 21)
- **Build Tool:** Gradle with Kotlin DSL and custom convention plugins
- **Database:** MongoDB Atlas via Quarkus MongoDB Panache
- **Frontend:** Server-side rendering with Quarkus Qute templates, htmx, Bootstrap 5
- **Reactive:** Mutiny (`Uni`, `Multi`) used pragmatically where beneficial
- **Quality:** Detekt (warnings-as-errors), EditorConfig

## Module Structure (Hexagonal Architecture)

```
adapter-in-web        – REST endpoints, OAuth, SSE, action handlers (Quarkus REST)
application-quarkus   – Main Quarkus application entry point and wiring
domain-api            – Domain contracts: interfaces, data classes, sealed result types
domain-impl           – Core business logic implementation
buildSrc              – Shared Gradle convention plugins (kotlin-project)
```

## Build & Test Commands

```bash
# Run full build (includes tests and static analysis)
./gradlew build

# Run tests only
./gradlew test

# Start application in dev mode (live reload)
./gradlew :application-quarkus:quarkusDev
# or use the helper script:
./dev.sh
```

## Architecture Patterns

- **Hexagonal Architecture:** Domain logic in `domain-api`/`domain-impl` is independent of framework adapters
- **Outbox Pattern:** Async reliability for Spotify API calls via a persistent task queue
- **CDI Events:** Used as the internal event bus between components
- **Reactivity:** Use Mutiny (`Uni`/`Multi`) where it adds value; do not apply reactivity dogmatically

## Coding Conventions

- **Write idiomatic Kotlin** — no Java-style code
- **Prefer `val` over `var`**; only use `var` when mutation is truly necessary
- **Avoid `!!`** — if unavoidable, add a comment explaining why it is safe
- **Data classes** for all domain model objects
- **Sealed classes** for result/state types
- **Extension functions** for type mappings and transformations
- **Max 3 return statements** per function (enforced by Detekt)
- **Max line length:** 170 characters (Detekt) / 180 characters (EditorConfig)
- **Code style:** Kotlin Official (configured via EditorConfig and `ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL`)
- **No star imports** (configured in EditorConfig)
- **Indentation:** 2 spaces

## Testing Conventions

- **Unit tests:** Plain Kotlin tests without framework, test domain logic in isolation
- **Integration tests:** Use `@QuarkusTest` for full Quarkus context
- **MongoDB:** Contract tests for aggregation pipelines
- Keep tests close to the module they test; do not mix unit and integration tests in the same class
