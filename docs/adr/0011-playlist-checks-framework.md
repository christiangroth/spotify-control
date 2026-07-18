# Playlist Checks Framework

* Status: accepted
* Deciders: Chris
* Date: 2026-03-14

## Context and Problem Statement

Manually curated Spotify playlists accumulate mistakes over time that Spotify itself never flags:
duplicate tracks, multiple tracks by the same artist ending up on a playlist meant to hold at most
one per artist, a "year" playlist drifting out of sync with the master "all" playlist it should be
a subset of, or a track referencing an older release when a newer/better version (e.g. a full
album vs. the single) has since become available. Left unchecked, these issues are only ever
noticed by chance.

How should the application detect and, where possible, correct these playlist-quality issues
without hand-rolling one-off scripts for each rule?

## Decision Drivers

* New check rules will be added over time as more issues are noticed — the mechanism must not
  require touching a central dispatcher every time.
* Not every check applies to every playlist (e.g. "single artist track" only makes sense for
  playlists explicitly meant to hold one track per artist); applicability must be declarative per
  check, not hardcoded call-site conditionals.
* Some violations can be corrected automatically against the live Spotify playlist (duplicates
  removed, missing tracks added, outdated releases swapped); others can only be reported. Both
  must be representable without forcing every check to implement a fix.
* Checks should run automatically after playlist data changes, not require a manual trigger, but
  must not fire so often that Slack notifications become noise.

## Considered Options

1. **Hardcoded check sequence** — a single service method that runs a fixed list of if/else rule
   checks inline.
2. **Pluggable `PlaylistCheckRunner` strategy interface**, CDI-discovered, each check its own bean
   with `isApplicable()`, `check()`, and an optional `fix()`.

## Decision Outcome

Chosen option: **"Pluggable `PlaylistCheckRunner` strategy interface"**, because new checks are
added as independent, self-contained beans without modifying any dispatch logic, and per-check
`isApplicable()` keeps playlist-type scoping declarative and local to the check itself instead of
scattered through a central conditional.

* `PlaylistCheckService` looks up all CDI-discovered `PlaylistCheckRunner` beans, filters by
  `isApplicable(playlistInfo)`, and runs the applicable ones concurrently via `ManagedExecutor`.
* Each result (`AppPlaylistCheck`: checkId, playlistId, timestamp, pass/fail, violations) is
  upserted into `app_playlist_check` with a deterministic `"$playlistId:$checkId"` id.
* Checks run automatically whenever `PlaylistService.syncPlaylistData()` finishes syncing a
  changed playlist (`RunPlaylistChecks` enqueued on the last page) — driven by the existing hourly
  `PlaylistSyncJob`, not a separate schedule.
* A Slack notification fires only when a check's pass/fail state flips or its violation set
  changes, avoiding repeat-notification noise for a violation that hasn't changed since the last
  check.
* Fixable checks expose `fix()`; a successful fix re-enqueues `SyncPlaylistData` to pull the
  corrected state back down and re-run checks, rather than assuming the fix succeeded locally.

### Positive Consequences

* Adding a new check is a self-contained new class, not a change to a shared dispatch method.
* Playlist-type scoping (`ALL`/`YEAR`/`SINGULARITY`) lives with the check that needs it.
* Not every check needs a `fix()` — reporting-only checks are equally well supported.
* Automatic re-run after sync means checks stay current without any manual "run checks" step.

### Negative Consequences

* A strategy-pattern/CDI-discovery indirection is more machinery than a single hardcoded method
  for the current, still-small number of checks.
* Concurrent execution via `ManagedExecutor` means check failures/timing are less linear to trace
  than a simple sequential loop would be.

## Pros and Cons of the Options

### Hardcoded check sequence

* Good, because it is the simplest possible implementation for a handful of checks.
* Bad, because every new check requires editing the same central method, and per-check
  applicability logic accumulates as conditionals rather than staying local to each check.

### Pluggable `PlaylistCheckRunner` strategy interface

* Good, because new checks are additive — no shared dispatch code changes.
* Good, because applicability, fixability, and check logic are all owned by the same class.
* Bad, because it introduces an interface and CDI-discovery indirection that a fixed, small check
  list doesn't strictly need yet.

## Links

* [arc42.md](../arc42/arc42.md)
