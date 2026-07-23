# CLAUDE.md for spotify-control

## Build & Test Commands

```bash
# Run full build (includes tests and static analysis)
./gradlew build

# Run tests only
./gradlew test

# Start application in dev mode (live reload)
./gradlew :application-quarkus:quarkusDev
```

## Green Build Requirement for PRs

**Every PR must have a green CI build before merging.** Follow these rules:

- **Never use `[no ci]`** in commit messages to bypass CI. All commits to a PR branch must be validated by CI.
- **Run `./gradlew build` before pushing** to verify the build, tests, and static analysis all pass.
- **CI must be green** on the PR branch before requesting or completing a merge into `main`.
- If CI fails on a PR, fix the underlying issue — do not skip CI.

## Formatting

All code must follow the formatting rules in `.editorconfig`. The most important rules for Kotlin:

- **2-space indentation** (not 4), no tabs
- **LF line endings**
- **Max line length:** 180 characters
- **Insert final newline** in every file

Always format new and edited files according to `.editorconfig` before committing.

## Documentation

- **Architecture:** [docs/arc42/arc42.md](docs/arc42/arc42.md)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](docs/coding-guidelines/role-frontend-developer.md)
- **Test engineer role guidelines:** [docs/coding-guidelines/role-test-engineer.md](docs/coding-guidelines/role-test-engineer.md)

## Logging

### General principles

- **Log at INFO only for meaningful state transitions** — scheduled job triggers, explicit user actions (rebuild, sync enqueue), and non-trivial outcomes (new data persisted, catalog enriched). Do not log routine adapter operations (every repository save/delete/upsert).
- **No per-item or per-poll logs at INFO** — operations that fire on every poll cycle (e.g. playback fetch every ~30 s) or for every individual record during a batch/backfill must not produce INFO output. Remove such log statements entirely rather than downgrading to DEBUG.
- **Repository adapters do not log** — the domain layer already provides context-rich logs for every meaningful operation. Adapter-level save/persist/update logs duplicate this without adding value and must not be added.
- **Guard-clause exits are silent** — early returns triggered by "user not found", "playlist not active", and similar expected guard conditions must not emit WARN or INFO. These are normal control flow in a single-user system.

### Names, not IDs

Whenever a human-readable name is available on the domain object being logged, include it alongside the ID:
- Tracks: `'${item.trackName}' by ${item.artistNames.joinToString()} (${item.trackId.value})`
- Artists: `'${artist.artistName}' ($artistId)`
- Albums: `'${album.title ?: albumId}' ($albumId)`
- Playlists: `'${playlist.name}' ($playlistId)`

Do not add a database lookup solely to obtain a name for a log message.

### What to keep at INFO

| Pattern | Example |
|---------|---------|
| Scheduled job triggers one meaningful outcome | `"Running scheduled playlist sync"` |
| Explicit rebuild / resync initiated | `"Rebuilding playback data"` |
| New catalog entity synced with name | `"Synced album '${title}' ($id): ${n} track(s)"` |
| Outbox pagination completed | `"Completed all pages for playlist '${name}' ($id)"` |
| Batch rebuild summary | `"Enqueued daily aggregations from $from to $to"` |
| User-visible state change | `"Updated sync status for playlist '${name}' ($id) to $syncStatus"` |

## Release Note Snippets

**Snippet filename:** `docs/releasenotes/snippets/{branch-last-segment}-{type}.md` where `{type}` is one of `bugfix` or `feature`.

**Snippet content:** Briefly describe what was changed or added on the branch. Each line should follow the pattern `* Description of the change.` Feel free to use multiple short lines, describing the change without technical detail. Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or internal structural changes (e.g. package renames, build task additions).

**Type selection:** Use `feature` for new user-facing functionality. Use `bugfix` for fixes and chore/internal changes (e.g. refactoring, configuration restructuring, dependency updates).

## Abschluss einer Aufgabe
Eine Aufgabe gilt erst als fertig, wenn:
1. Build/Tests grün sind (im Hintergrund + Polling, siehe oben)
2. Änderungen committet und gepusht sind
3. Ein PR via `gh pr create` (oder `gh pr edit`, falls schon vorhanden) offen ist
Kein Task endet mit "PR wird noch erstellt" als offenem Punkt.

