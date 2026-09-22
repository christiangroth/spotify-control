# Outbox groupId Adoption and Delayed Dispatch Evaluation

* Status: accepted
* Deciders: Chris
* Date: 2026-09-22

Technical Story: [#928](https://github.com/christiangroth/spotify-control/issues/928)

## Context and Problem Statement

[ADR-0007](0007-persistent-outbox-pattern.md) chose a persistent outbox with one worker per
partition, giving strict in-order processing within a partition. `quarkus-outbox` 0.9.0 added an
optional per-task `groupId` plus a `workerCount` on `ApplicationOutboxPartition`: tasks sharing a
`groupId` within a partition still process strictly in order, but different `groupId`s can run
concurrently across multiple workers. 0.10.0 added delayed/scheduled dispatch
(`ApplicationOutboxClient.enqueue(event, notBefore)`, `cancel`, `reschedule`). Both are optional and
backward compatible (`groupId` defaults to none, `workerCount` defaults to 1).

Two of the five partitions serialize genuinely unrelated work behind a single worker:

* **`domain`** — the catch-all partition, carrying per-artist confirmations
  (`ConfirmArtistSync`/`ConfirmArtistShallow`), per-playlist checks (`RunPlaylistChecks`), playback
  aggregation, and read-model rebuilds (see [ADR-0014](0014-precomputed-read-models-per-ui-page.md)).
  An artist-sync confirmation for artist A has no ordering relationship with a playlist check for
  playlist B, yet both sit behind the same single-worker queue today.
* **`to-spotify-catalog`** — mixes artist-scoped (`SyncArtistDetails`, `SyncArtistAlbums`) and
  album-scoped (`SyncAlbumDetails`) Spotify API calls.

Separately, `PlaybackAggregationJob` has five `@Scheduled` cron methods (daily/weekly/monthly/
quarterly/yearly), each enqueuing its own `AggregatePlaybackData` event — a plausible candidate for
delayed dispatch's `notBefore`, collapsing into one self-re-enqueuing chain.

## Decision Drivers

* The `domain` and `to-spotify-catalog` partitions throttle unrelated entities behind each other for
  no ordering reason, adding latency under load without any correctness benefit.
* Per-entity ordering must be preserved: two events for the *same* artist/playlist/album must still
  process in the order they were enqueued.
* `to-spotify-catalog` calls the rate-limited Spotify API, so added concurrency there must stay
  modest.
* Complexity should match real value (see [role-architect.md](../coding-guidelines/role-architect.md))
  — `groupId` is a small annotation-level change with a clear throughput win; a hand-rolled
  "next occurrence" calculator for five different cron periodicities is real, unforced complexity for
  a benefit that is at most cosmetic.

## Decision Outcome

**Adopted `groupId` + `workerCount` on `domain` and `to-spotify-catalog`.** Every event in these two
partitions now sets a `groupId`:

* The relevant entity id where one exists: `artistId` (`SyncArtistDetails`, `SyncArtistAlbums`,
  `ConfirmArtistSync`, `ConfirmArtistShallow`), `albumId` (`SyncAlbumDetails`), `playlistId`
  (`RunPlaylistChecks`).
* Otherwise the event's own key as a fixed fallback group, e.g. `RebuildDashboardReadModel`,
  `AggregatePlaybackData`, `ResyncCatalog`. This keeps multiple instances of the *same* event type
  serialized against each other (two dashboard rebuilds still run in order) while letting it run
  concurrently with unrelated event types (a dashboard rebuild no longer blocks an artist sync).

`domain` gets `workerCount = 4` (internal, DB-bound work — safe to parallelize more aggressively).
`to-spotify-catalog` gets `workerCount = 3` (bounds added concurrent Spotify API calls to a modest
number rather than fully unbounded parallelism).

The other three partitions (`to-spotify-playback`, `to-spotify-user`, `to-spotify-playlist`) are left
single-worker/ungrouped: each is either a single event type or already reasonably scoped, so there is
no concrete ordering/throughput problem to fix. Re-evaluate only if one shows up.

**Not adopting delayed dispatch for `PlaybackAggregationJob` (Task 3 closed as "not worth it").** A
`@Scheduled(cron = ...)` per fixed period is simple, declarative, and already correct for all five
periodicities. Replacing it with a self-re-enqueuing chain via `enqueue(event, notBefore =
nextOccurrence)` would require hand-computing "next occurrence" for daily/weekly/monthly/quarterly/
yearly boundaries — with real DST and month-length edge cases (28–31 day months, leap years, leap
seconds are not a concern but calendar month arithmetic is) — to collapse five well-understood,
already-correct annotations into one more complex code path. The benefit (one code path instead of
five) is cosmetic; the risk (a subtly wrong "next occurrence" calculation silently skipping or
doubling a period) is not worth taking on. `notBefore` remains available for future use cases with a
genuinely dynamic schedule (e.g. user-triggered "run again after X" flows), just not this one.

### Positive Consequences

* Artist syncs, playlist checks, and read-model rebuilds no longer queue behind each other
  unnecessarily; latency for unrelated domain work drops under load.
* Different artists/albums sync concurrently in `to-spotify-catalog` while still respecting Spotify's
  need for per-entity ordering (e.g. `SyncArtistAlbums` pages for the same artist stay in order).
* No behavior change for the three partitions left untouched, and no schema/API change — `groupId`
  and `workerCount` are additive fields on the existing event/partition model.

### Negative Consequences

* Slightly more state to reason about per event (which group it belongs to) when adding new event
  types to `domain` or `to-spotify-catalog` — a new event type must pick the right `groupId` or fall
  back to its own key.
* `to-spotify-catalog` at `workerCount = 3` triples the worst-case concurrent Spotify API call count
  from that partition; if this proves too aggressive under real rate-limit pressure, `workerCount`
  can be dialed back down to 1–2.

## Links

* Refines [ADR-0007](0007-persistent-outbox-pattern.md)
* Relates to [ADR-0014](0014-precomputed-read-models-per-ui-page.md) (read-model rebuild events)
* [quarkus-outbox library](https://github.com/christiangroth/quarkus-outbox)
