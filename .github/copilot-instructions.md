# Copilot Instructions for spotify-control

## Build & Test Commands

> **Important:** The build requires a `GHCR_PAT` environment variable (a GitHub Personal Access Token
> with `read:packages` scope) to resolve dependencies and plugins from private GitHub Package
> Registries (`christiangroth/gradle-release-notes-plugin`, `christiangroth/quarkus-outbox`,
> `christiangroth/quarkus-starters`). It also requires Java 25. Copilot agents do not have access to
> these credentials – **do not attempt to run any Gradle commands**.

```bash
# Run full build (includes tests and static analysis)
# Requires: GHCR_PAT env var, Java 25 (JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64)
GHCR_PAT=$(gh auth token) JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 ./gradlew build

# Run tests only
GHCR_PAT=$(gh auth token) JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 ./gradlew test

# Start application in dev mode (live reload)
GHCR_PAT=$(gh auth token) JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 ./gradlew :application-quarkus:quarkusDev
```

## Documentation

- **Architecture:** [docs/arc42/arc42.md](../docs/arc42/arc42.md)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](../docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](../docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](../docs/coding-guidelines/role-frontend-developer.md)

## Release Note Snippets

**Snippet filename:** `docs/releasenotes/snippets/{branch-last-segment}-{type}.md` where `{type}` is one of `bugfix` or `feature`.

**Snippet content:** Briefly describe what was changed or added on the branch. Each line should follow the pattern `* {branch-last-segment}: Description of the change.` Feel free to use multiple short lines, describing the change without technical detail. Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or internal structural changes (e.g. package renames, build task additions).

**Type selection:** Use `feature` for new user-facing functionality. Use `bugfix` for fixes and chore/internal changes (e.g. refactoring, configuration restructuring, dependency updates).
