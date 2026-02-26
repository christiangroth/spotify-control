# Copilot Instructions for spotify-control

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

## Documentation

- **Architecture:** [docs/arc42/arc42-EN.md](../docs/arc42/arc42-EN.md)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](../docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](../docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](../docs/coding-guidelines/role-frontend-developer.md)

## Release Note Snippets

Every branch (except `main` and `dependabot/*` branches) **must** contain at least one release note snippet, or the build will fail.

**When to create a snippet:** Always create a snippet as one of the first actions when working on any branch. Do not wait until the end of the task.

**How to create a snippet:** Run the appropriate Gradle task based on the type of change. **Always use the Gradle task** — it also handles the version bump automatically for non-Bugfix changes (Feature bumps the minor version, UpdateNotice bumps the major version):

```bash
# For new features (also bumps minor version: x.Y.z → x.(Y+1).0)
./gradlew releasenotesCreateFeature

# For bug fixes or chores (no version bump)
./gradlew releasenotesCreateBugfix

# For breaking changes / update notices (also bumps major version: X.y.z → (X+1).0.0)
./gradlew releasenotesCreateUpdateNotice

# For highlights (no version bump)
./gradlew releasenotesCreateHighlight
```

**Snippet location and naming:** Snippets are placed in `docs/releasenotes/releasenotes-snippets/` and follow the pattern `{branch-last-segment}-{type}.md` (e.g. for branch `feature/my-feature`, the file is `my-feature-feature.md`).

**Snippet content:** Edit the generated snippet file to briefly describe what was changed or added on the branch. Each line should follow the pattern `* {branch-last-segment}: Description of the change.`

Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or internal structural changes (e.g. package renames, build task additions).

See [docs/arc42/arc42-EN.md](../docs/arc42/arc42-EN.md) — section "Release Process" — for full details.
