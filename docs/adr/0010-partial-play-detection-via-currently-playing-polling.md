# Partial Play Detection via Currently-Playing Polling

* Status: accepted
* Deciders: Chris
* Date: 2026-03-06

## Context and Problem Statement

Spotify's `GET /v1/me/player/recently-played` endpoint only returns tracks played to completion.
Skipped songs never appear in that response, so any track the user starts but doesn't finish is
invisible to `recently_played`-based statistics — heard minutes are silently lost, and "what was I
listening to" history is incomplete for exactly the tracks a user is most likely to skip.

How can partially-played tracks be captured and reflected in playback statistics, given that
Spotify's API only exposes completed plays directly?

## Decision Drivers

* Spotify does not expose a "skipped" or "partial play" event anywhere in its API — it must be
  inferred from repeated polling of current playback state.
* The same track can be played, skipped, and replayed from the beginning multiple times in a
  session; each such attempt must be counted as a separate partial play, not merged into one.
* A still-active listening session must not be prematurely converted and recorded — it may resume
  or finish normally, which would otherwise create a duplicate record once it does complete.
* Estimated listen duration must be reasonably accurate without relying on data Spotify doesn't
  provide (there is no explicit "stopped listening at" event).

## Considered Options

1. **Rely solely on `recently-played`** — accept that skipped tracks are invisible to statistics.
2. **Poll `GET /v1/me/player/currently-playing` periodically and consolidate snapshots into
   partial-play records** at recently-played sync time.

## Decision Outcome

Chosen option: **"Poll `currently-playing` and consolidate"**, because it is the only way to
observe a track that Spotify itself never reports as a completed play, and the polling interval
already used for live "now playing" UI updates can be reused for statistics purposes at no extra
API cost.

* `GET /v1/me/player/currently-playing` is polled every 20 seconds (originally seconds 5/25/45 of
  each minute; later consolidated onto the single `PlaybackDetectionJob` schedule), persisting
  snapshots to `spotify_currently_playing`.
* At consolidation time (now on every `PlaybackDetectionJob` run, previously only at
  recently-played sync time): entries whose track ID already appears in the `recently-played`
  response are deleted as redundant (the full play is already recorded by Spotify). Remaining
  sessions whose observed progress exceeds a configurable minimum (default 25s) are converted to
  `RecentlyPartialPlayedItem` records and deleted from the currently-playing collection.
* **Session-based grouping**: contiguous polling observations of the same track form one session;
  the same track played twice in a row (played → skipped away → replayed from the start) produces
  two separate partial-play sessions, not one merged entry.
* **Active-session protection**: the most recently observed non-completed session is never
  converted — it may still be playing or paused and could resume or finish normally; converting it
  prematurely would create a duplicate record once it later completes.
* **Duration estimation**: `playedSeconds` is derived from the gap between a session's first
  observation and the first observation of a different track afterward in the global polling
  timeline (falling back to observed progress if no later observation exists), rather than trusting
  any single snapshot's `progress_ms` as the final value.

### Positive Consequences

* Skipped/partial listens are captured and reflected in statistics instead of silently
  disappearing.
* Reuses the same polling job that already drives the "currently playing" live UI — no separate
  infrastructure needed.
* Session grouping and active-session protection prevent both merged-session undercounting and
  premature-conversion duplicates.

### Negative Consequences

* Detection accuracy is bounded by polling frequency — very short plays near a session's edges can
  be missed or misclassified (tracked as a known limitation, see arc42 Technical Debts).
* Adds a second raw collection (`spotify_currently_playing`) and a consolidation step, rather than
  a single straightforward ingestion path from `recently-played` alone.
* The duration estimate is inferred, not authoritative — it can be off by up to one polling
  interval.

## Pros and Cons of the Options

### Rely solely on `recently-played`

* Good, because it needs no polling, no extra collection, and no session-inference logic.
* Bad, because every skipped track is invisible to statistics — a significant and systematic gap
  for exactly the tracks a user engages with least.

### Poll `currently-playing` and consolidate

* Good, because it is the only way to observe plays Spotify never reports as completed.
* Good, because the polling cadence needed for live UI updates already exists and can be shared.
* Bad, because session detection, active-session protection, and duplicate-consolidation logic add
  real complexity that a pure `recently-played` ingestion would not need.

## Links

* [arc42.md](../arc42/arc42.md)
