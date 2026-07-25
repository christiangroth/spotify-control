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
- **Exception for time-limited agent sessions:** see "Running Gradle in Time-Limited Agent Sessions" below — the full local `./gradlew build` is replaced by scoped commands plus watching CI in that context.

## Running Gradle in Time-Limited Agent Sessions

This applies whenever a single command execution is time-limited (e.g. the Claude Code GitHub Action, whose Bash tool has a hard execution ceiling of a few minutes per call). A full multi-module `./gradlew build` (all modules, tests, static analysis) can exceed that ceiling, and gets killed at an inconsistent point from run to run — do not rely on it in this context.

- **Never background a Gradle invocation** (no `run_in_background` / async execution). A build that keeps running after its tool call is considered "timed out" produces results that cannot be trusted or awaited.
- **Scope the build to the module(s) you touched** for a fast, reliable local signal, e.g. `./gradlew :module-name:test` or `:module-name:compileKotlin` instead of the whole-repo `build`.
- **Push the branch and open the PR**, then rely on this repository's `Java CI/CD with Gradle` workflow (triggered automatically on push) as the authoritative full-build gate. Poll with `gh pr checks` (short, non-blocking calls) to confirm it goes green rather than reproducing the whole build locally.

This supersedes the general "run `./gradlew build` before pushing" rule above only in these time-limited contexts; a normal local developer session with no such execution ceiling should still run the full build.

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
1. Die für die geänderten Module relevanten Tests/Checks grün sind (siehe "Running Gradle in Time-Limited Agent Sessions" für den Ausführungsmodus), und der reguläre CI-Workflow nach dem Push grün ist oder zumindest läuft.
2. Änderungen committet und gepusht sind.
3. Ein PR via `gh pr create` offen ist — vorher mit `gh pr list --head <branch>` prüfen, ob für den Branch schon einer existiert; falls ja, `gh pr edit`/normalen Push verwenden statt einen zweiten PR anzulegen.

Kein Task endet mit "PR wird noch erstellt" als offenem Punkt, und kein Task endet stillschweigend nach einer teilweisen Änderung. Scheitert ein Schritt (z. B. `git push` oder `gh pr create`), erneut versuchen (z. B. mit anderem Branch-Namen); schlägt es weiterhin fehl, den Grund explizit in einem Kommentar nennen. Ist nach Prüfung klar, dass keine Code-Änderung nötig ist, das ebenfalls explizit kommentieren statt ohne jede Rückmeldung zu enden.

## Inkrementelle Commits

Nicht bis zum Schluss warten und alles in einem Commit bündeln: nach jedem in sich abgeschlossenen Schritt committen und pushen. Sobald der erste sinnvolle Commit gepusht ist, direkt einen Draft-PR öffnen (`gh pr create --draft`) und die Beschreibung im Verlauf aktualisieren; erst am Ende aus dem Draft-Status nehmen. So bleibt auch bei einem abgebrochenen Lauf sichtbar, was bereits passiert ist, statt dass ein Run spurlos endet.

