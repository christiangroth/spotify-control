# Copilot Instructions for spotify-control

## Build & Test Commands

```bash
# Run full build (includes tests and static analysis)
./gradlew build

# Run tests only
./gradlew test

# Start application in dev mode (live reload)
./gradlew :application-quarkus:quarkusDev
```

## Documentation

- **Architecture:** [docs/arc42/arc42.md](../docs/arc42/arc42.md)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](../docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](../docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](../docs/coding-guidelines/role-frontend-developer.md)

## Release Note Snippets

Every branch (except `main` and `dependabot/*` branches) **must** contain at least one release note snippet, or the build will fail.

**When to create a snippet:** Always create a snippet as one of the first actions when working on any branch. Do not wait until the end of the task.

**How to create a snippet:** Run the appropriate Gradle task based on the type of change. **NEVER create snippet files manually** — always use the Gradle task, which also handles the version bump automatically:

- `releasenotesCreateFeature` — new user-facing functionality; **automatically bumps the minor version**
- `releasenotesCreateBugfix` — bugfix, chore, or documentation change; no version bump
- `releasenotesCreateUpdateNotice` — breaking change; **automatically bumps the major version**

```bash
# feature (bumps minor version automatically, e.g. 0.10.x → 0.11.0)
./gradlew releasenotesCreateFeature

# bugfix/chore/documentation (no version bump)
./gradlew releasenotesCreateBugfix

# breaking change (bumps major version automatically, e.g. 0.x.y → 1.0.0)
./gradlew releasenotesCreateUpdateNotice
```

**Snippet content:** Edit the generated snippet file to briefly describe what was changed or added on the branch. Each line should follow the pattern `* {branch-last-segment}: Description of the change.`

Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or internal structural changes (e.g. package renames, build task additions).
