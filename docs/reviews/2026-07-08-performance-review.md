# Performance Review — 2026-07-08

## Context

This review was requested in [issue #732](https://github.com/christiangroth/spotify-control/issues/732), with an explicit focus on performance rather than just "will a few extra users cause CPU load". The request specifically asked to look beyond raw user count and consider the data model, missing coaching/expertise, and other structural causes.

The review covers the MongoDB data model and indexes, the playback/catalog processing pipeline, dashboard aggregation, scheduler jobs, and general Kotlin/Quarkus patterns. Findings are based on reading the current source, not assumptions from the architecture docs alone.

> **Update, 2026-07-09:** [ADR-0008](../adr/0008-single-user-architecture.md) converted the
> application to a strict single-user architecture, implemented incrementally across several
> follow-up PRs. Findings 1 and 2 below, and the "what breaks first" multi-user scalability
> section, are resolved by eliminating their precondition — there is no user count left to scale.
> See the inline notes on each.

## What is already done well

Not everything here is a problem — several mechanisms are already built with scale in mind and should be kept as-is:

- **Indexes are deliberately maintained.** `MongoIndexInitializer` (`adapter-out-mongodb/src/main/kotlin/.../MongoIndexInitializer.kt:25-119`) creates compound indexes for every hot query path (`(spotifyUserId, playedAt)` on playback collections, `(spotifyUserId, trackId, observedAt)` on currently-playing, `(spotifyUserId, type, periodStart)` on aggregations, catalog `lastSync`/`artistId`/`albumId`, and now `spotifyUserId` on `spotify_playlist` as well). This is the opposite of a "missing coaching" issue — it's a good pattern that is consistently applied across collections.
- **`appendPlaybackData` is incremental, not a full rescan.** It looks up the most recent `playedAt` via an indexed query and only processes the delta since then (`domain-impl/.../PlaybackService.kt:281-310`). Its cost does not grow with total listening history.
- **Bulk writes, not per-item upserts.** Artist/album/track sync all build `UpdateOneModel` lists and use `bulkWrite(..., ordered = false)` (`AppArtistRepositoryAdapter.kt:22-43`, `AppAlbumRepositoryAdapter.kt:23-50`, `AppTrackDataRepositoryAdapter.kt:24-52`). Spotify playlist track add/remove is chunked into batches of 100 instead of one call per track (`SpotifyPlaylistAdapter.kt:176-230`).
- **Aggregation is precomputed and bounded.** `PlaybackAggregationService` computes daily rollups once per day and merges them into week/month/quarter/year, with an explicit cap (`STORED_ENTRIES_LIMIT = 25`, `PlaybackAggregationService.kt:300-302`) after a previously-fixed bug where merged rank lists grew unbounded. The dashboard reads these precomputed documents rather than raw events.
- **Spotify API usage is batch-aware.** Catalog and playlist adapters use Spotify's own pagination and bulk endpoints (`GET /v1/artists?ids=`, `GET /v1/albums/{id}`, playlist `offset`/`next`) rather than looping per item.
- **Adaptive polling.** `CurrentlyPlayingSkipPredicate` (`CurrentlyPlayingSkipPredicate.kt:35-42`) backs off from 20s to 5 min polling when no playback is active, and `ArtistCatalogSyncJob` spreads catalog resync across 14 days (`ArtistCatalogSyncJob.kt:19-22`) to respect Spotify rate limits.

These are worth calling out explicitly: the codebase does not read as "written without performance awareness" — the issues below are specific and mostly isolated, not systemic.

## Findings

### 1. Single-user shortcut drives shared catalog sync for everyone (Medium-High, multi-user readiness)

Several `CatalogService` operations authenticate catalog-wide Spotify calls using an arbitrary user: `userRepository.findAll().firstOrNull()?.spotifyUserId` (`CatalogService.kt:103, 114, 134, 185, 197`, also `CatalogBrowserService.kt:214`). This means catalog enrichment for *all* users' data depends on whichever user happens to be first in an otherwise-unordered `findAll()` result staying authenticated.

This is a design smell that gets worse, not better, as the user base grows: more users means a higher chance that the "first" user is inactive or has a stale/revoked token, silently stalling catalog enrichment for everyone with no fallback.

**Action:** either round-robin/fall back across all authenticated users, or make catalog sync operations independent of any single user's token (e.g. a dedicated service-account style token, or explicit fallback to the next valid user on failure).

**Resolved (2026-07-09):** the single-user migration ([ADR-0008](../adr/0008-single-user-architecture.md)) removed the arbitrary-user pick entirely — catalog sync now resolves the one stored user via `CurrentUserResolver`, so there is no "wrong user picked" failure mode left to harden against.

### 2. Potential single-partition serialization bottleneck for playback polling (Medium-High, needs confirmation)

Per `docs/arc42/arc42.md:401` all playback-fetch events route through a single `to-spotify-playback` outbox partition with no throttle. If that partition is drained by one worker at a time (this is implemented in the external `de.chrgroth.quarkus.outbox` library, not this repo), every 20-second poll cycle enqueues one event per user into a partition processed sequentially. As user count grows from 1-2 to 20-50, the time to drain the partition (N × [Spotify round trip + Mongo write]) could approach or exceed the 20s interval, causing a growing backlog and stale currently-playing data for all users, not just the one that's slow.

**Action:** confirm the concurrency model of `OutboxPartitionWorker` in the outbox library. If it is strictly single-worker-per-partition, consider splitting `to-spotify-playback` per user or increasing partition worker concurrency before onboarding significantly more users.

**Resolved (2026-07-09):** the single-user migration ([ADR-0008](../adr/0008-single-user-architecture.md)) means `to-spotify-playback` only ever carries events for the one user; per-user sub-partitioning is no longer a meaningful mitigation since there is nothing to partition by.

### 3. No pagination on catalog list ports (Low)

`AppArtistRepositoryPort.findAll()`, `AppAlbumRepositoryPort.findAll()`, `AppTrackRepositoryPort.findAll()` have no page/limit parameter, unlike `findRecentlySynced(offset, limit)` which already exists on the same adapters. The catalog browse endpoints (`CatalogBrowserService.getArtists`/`getAlbums`) have since been moved onto bounded `searchByName`/`searchByTitle` queries instead of `findAll()`, but `CatalogService` still calls `appArtistRepository.findAll()` directly for artist-wide sync operations, so the unbounded read path remains for that use case.

**Action:** add pagination (or a bounded batched-iteration pattern) to the remaining `CatalogService` call sites that rely on `findAll()`, so there is no unbounded read path left for a catalog that is designed to keep growing.

### 4. Per-user full reload of `currently_playing` set every poll cycle (Low)

`convertAndDeleteOrphanedItems` / `convertPartialPlays` (`PlaybackService.kt:114-149, 224-266`) reload the full currently-playing set per user every cycle rather than only new observations. Because entries are deleted immediately after conversion, this stays bounded to "currently active tracks per user" — it does not scale with history, only linearly with user count (one query per user per cycle instead of one batched query for all users).

**Action:** low priority; only worth batching into a single `$in`-based query if user count grows enough that per-cycle query volume itself becomes a concern (see Finding 2, which will bite first).

### 5. `CatalogBrowserService.getCatalogStats()` recomputed on every `/dashboard` and `/catalog` page load (Medium)

`getCatalogStats()` (`CatalogBrowserService.kt:36-45`) issues three uncached `countAll()` calls. It is invoked on every `/dashboard` request via `DashboardService.getStats` (`DashboardService.kt:63, 78`) *and* on every `/catalog` request via `CatalogResource.catalog` (`CatalogResource.kt:41`) — both are pages a logged-in user is likely to land on repeatedly per session. `DashboardService.getStats` also runs `playlistCheckRepository.countAll()`/`countSucceeded()` (`DashboardService.kt:144-145`) on the same request, with the same "recomputed every page view, never cached" characteristic.

Unlike the `DomainMetrics` catalog gauges (now fixed), these aren't triggered by an external scrape interval, so the request volume scales with page views rather than a fixed 15s cadence — at low user counts this is negligible, but it's counted queries that don't need to be fresher than "last catalog sync", which runs at most a few times a day (`ArtistCatalogSyncJob.kt:19-22`).

**Action:** apply the same short-TTL in-memory cache to `getCatalogStats()` (and, if convenient, `playlistCheckRepository.countAll()`/`countSucceeded()`) rather than recomputing on every page load — a 15-30s cache is more than fresh enough for counts that only change on catalog sync.

## Action Items (prioritized)

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 1 | Remove the single-user shortcut for shared catalog sync operations; add fallback across authenticated users | Medium-High | Medium |
| 2 | Confirm `to-spotify-playback` outbox partition concurrency model; plan for per-user partitioning or increased concurrency before scaling past current user count | Medium-High | Needs investigation (external library) |
| 3 | Add pagination (or a bounded iteration pattern) to the remaining unpaginated `findAll()` catalog port call sites in `CatalogService` | Low | Small |
| 4 | Batch per-user `currently_playing` reload into a single multi-user query, if/when user count grows significantly | Low | Medium |
| 5 | Cache `CatalogBrowserService.getCatalogStats()` (and playlist check counts) with a short TTL instead of recomputing on every `/dashboard`/`/catalog` load | Medium | Small |

## Multi-user scalability — what breaks first

**Resolved (2026-07-09):** this section described risks in a multi-user world that no longer exists after [ADR-0008](../adr/0008-single-user-architecture.md)'s single-user migration. Kept below for historical context only — none of these need action.

Ranked by how soon each mechanism is expected to become a visible problem as the user base grows from 1-2 to 20-50 users:

1. **`to-spotify-playback` partition throughput (Finding 2)** — if single-worker-per-partition, degrades silently (staler data, no errors) as user count rises.
2. **Single-user catalog-sync shortcut (Finding 1)** — not a throughput issue, but a robustness gap that becomes more likely to trigger as more users (and more chances of one stale token) are added.
3. **Uncached `getCatalogStats()` on `/dashboard`/`/catalog` (Finding 5)** — scales with page-view frequency, not data size; low absolute cost per call but adds up with more concurrent users.
4. **Per-user `currently_playing` reload (Finding 4)** — scales linearly with user count, but stays small in absolute terms until user count is quite large.

By contrast, `appendPlaybackData`'s incremental design and the precomputed aggregation pipeline are already built to scale with new data rather than total history, and should not need changes as either history or user count grows.

## On "missing coaching" as a root cause

The request explicitly asked whether missing coaching or expertise gaps — not just multi-user load — explain the performance picture here. Based on this review, the codebase does not show broad signs of inexperience: indexing, incremental processing, aggregation bounding, and Spotify batch API usage are all handled correctly in the majority of the code. The issues found are localized:

- One structural single-point-of-failure decision (Finding 1) that likely came from building for the initial single/dual-user case and not revisiting it as multi-user support was added.

A handful of "wrote it the straightforward way, not the scalable way" gaps (the missing playlist index and track-count query, the unbounded catalog search, the sequential stats lookups, the in-memory max, and the catalog gauge caching) and a stylistic gap around coroutine usage in blocking code have since been fixed.

None of these indicate a need for a broad re-architecture; they're targeted fixes.
