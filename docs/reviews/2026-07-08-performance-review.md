# Performance Review — 2026-07-08

## Context

This review was requested in [issue #732](https://github.com/christiangroth/spotify-control/issues/732), with an explicit focus on performance rather than just "will a few extra users cause CPU load". The request specifically asked to look beyond raw user count and consider the data model, missing coaching/expertise, and other structural causes.

The review covers the MongoDB data model and indexes, the playback/catalog processing pipeline, dashboard aggregation, scheduler jobs, and general Kotlin/Quarkus patterns. Findings are based on reading the current source, not assumptions from the architecture docs alone.

## What is already done well

Not everything here is a problem — several mechanisms are already built with scale in mind and should be kept as-is:

- **Indexes are deliberately maintained.** `MongoIndexInitializer` (`adapter-out-mongodb/src/main/kotlin/.../MongoIndexInitializer.kt:25-119`) creates compound indexes for every hot query path (`(spotifyUserId, playedAt)` on playback collections, `(spotifyUserId, trackId, observedAt)` on currently-playing, `(spotifyUserId, type, periodStart)` on aggregations, catalog `lastSync`/`artistId`/`albumId`). This is the opposite of a "missing coaching" issue — it's a good pattern that should be extended to the one collection that lacks it (see Finding 1).
- **`appendPlaybackData` is incremental, not a full rescan.** It looks up the most recent `playedAt` via an indexed query and only processes the delta since then (`domain-impl/.../PlaybackService.kt:281-310`). Its cost does not grow with total listening history.
- **Bulk writes, not per-item upserts.** Artist/album/track sync all build `UpdateOneModel` lists and use `bulkWrite(..., ordered = false)` (`AppArtistRepositoryAdapter.kt:22-43`, `AppAlbumRepositoryAdapter.kt:23-50`, `AppTrackDataRepositoryAdapter.kt:24-52`). Spotify playlist track add/remove is chunked into batches of 100 instead of one call per track (`SpotifyPlaylistAdapter.kt:176-230`).
- **Aggregation is precomputed and bounded.** `PlaybackAggregationService` computes daily rollups once per day and merges them into week/month/quarter/year, with an explicit cap (`STORED_ENTRIES_LIMIT = 25`, `PlaybackAggregationService.kt:300-302`) after a previously-fixed bug where merged rank lists grew unbounded. The dashboard reads these precomputed documents rather than raw events.
- **Spotify API usage is batch-aware.** Catalog and playlist adapters use Spotify's own pagination and bulk endpoints (`GET /v1/artists?ids=`, `GET /v1/albums/{id}`, playlist `offset`/`next`) rather than looping per item.
- **Adaptive polling.** `CurrentlyPlayingSkipPredicate` (`CurrentlyPlayingSkipPredicate.kt:35-42`) backs off from 20s to 5 min polling when no playback is active, and `ArtistCatalogSyncJob` spreads catalog resync across 14 days (`ArtistCatalogSyncJob.kt:19-22`) to respect Spotify rate limits.

These are worth calling out explicitly: the codebase does not read as "written without performance awareness" — the issues below are specific and mostly isolated, not systemic.

## Findings

### 1. Missing index on `spotify_playlist`, and it stores full track lists (High)

`MongoIndexInitializer` never creates an index for the `spotify_playlist` collection. `PlaylistRepositoryAdapter.findTrackCountsByUserId` (`adapter-out-mongodb/.../PlaylistRepositoryAdapter.kt:53-58`) queries it by `spotifyUserId` — an unindexed collection scan. Each document also embeds the *entire* track list per playlist (`PlaylistDocument.tracks: List<PlaylistTrackSubdocument>`), so the query pulls every track of every playlist just to compute `it.tracks.size`. This runs on every load of `/playlists/settings` (`PlaylistsResource.kt:50`, `PlaylistSettingsResource.kt:47`).

At today's scale (1-2 users, modest playlists) this is cheap. At 20-50 users with larger playlists, it becomes an unindexed scan transferring full track arrays on a page that will be opened frequently.

**Action:** add an index on `spotifyUserId` in `MongoIndexInitializer`, and replace the in-memory `size` count with a `$project: { trackCount: { $size: "$tracks" } }` aggregation (or a separate lightweight counts collection) so track data never needs to leave MongoDB for this query.

### 2. Catalog browser loads the entire shared catalog per search request (High)

`CatalogBrowserService.getArtists` / `getAlbums` (`CatalogBrowserService.kt:47-73`, `75-97`) call `appArtistRepository.findAll()` / `appAlbumRepository.findAll()` / `appTrackRepository.findAll()` unconditionally on every non-blank filter, then filter/group in Kotlin. This backs `CatalogResource.artistList` (`adapter-in-web/.../CatalogResource.kt:51-62`), an HTMX-style fragment endpoint that reads like it fires on every keystroke.

Because the catalog is shared across all users and only ever grows (trimmed solely via a full `wipeCatalog()` admin action), this is the clearest "will this scale" problem in the codebase: search latency is proportional to total catalog size, on every keystroke, and it degrades both with more users (more distinct artists/tracks ever played) and simply over time with a fixed user count.

**Action:** push the filter into MongoDB (`$regex` or a text index on artist/album name) with a `limit`, instead of loading the full collection into the JVM. `findAll()` should not exist as an unbounded port method for a collection designed to keep growing — the existing paginated `findRecentlySynced(offset, limit)` pattern (`AppArtistRepositoryAdapter.kt:81-90`) is the right model to follow here.

### 3. Single-user shortcut drives shared catalog sync for everyone (Medium-High, multi-user readiness)

Several `CatalogService` operations authenticate catalog-wide Spotify calls using an arbitrary user: `userRepository.findAll().firstOrNull()?.spotifyUserId` (`CatalogService.kt:103, 114, 134, 185, 197`, also `CatalogBrowserService.kt:214`). This means catalog enrichment for *all* users' data depends on whichever user happens to be first in an otherwise-unordered `findAll()` result staying authenticated.

This is a design smell that gets worse, not better, as the user base grows: more users means a higher chance that the "first" user is inactive or has a stale/revoked token, silently stalling catalog enrichment for everyone with no fallback.

**Action:** either round-robin/fall back across all authenticated users, or make catalog sync operations independent of any single user's token (e.g. a dedicated service-account style token, or explicit fallback to the next valid user on failure).

### 4. Potential single-partition serialization bottleneck for playback polling (Medium-High, needs confirmation)

Per `docs/arc42/arc42.md:401` all playback-fetch events route through a single `to-spotify-playback` outbox partition with no throttle. If that partition is drained by one worker at a time (this is implemented in the external `de.chrgroth.quarkus.outbox` library, not this repo), every 20-second poll cycle enqueues one event per user into a partition processed sequentially. As user count grows from 1-2 to 20-50, the time to drain the partition (N × [Spotify round trip + Mongo write]) could approach or exceed the 20s interval, causing a growing backlog and stale currently-playing data for all users, not just the one that's slow.

**Action:** confirm the concurrency model of `OutboxPartitionWorker` in the outbox library. If it is strictly single-worker-per-partition, consider splitting `to-spotify-playback` per user or increasing partition worker concurrency before onboarding significantly more users.

### 5. `StatsResource` does 15 sequential lookups per page view (Medium)

`StatsResource.stats()` (`StatsResource.kt:49-66`) loops 5 `AggregationPeriodType` values × 3 period-starts, calling `aggregationRepository.findByUserAndPeriod(...)` individually — 15 separate blocking round-trips per `/stats` view. Documents are keyed by `"${userId}:${type}:${periodStart}"` (`PlaybackAggregationRepositoryAdapter.kt:156-157`), so these could be resolved with a single `$in` / `findByIds` batch call.

This doesn't get worse as data grows (each lookup is a fast keyed read), but it multiplies fixed per-request latency by concurrent page views.

**Action:** batch the 15 lookups into one query.

### 6. In-memory max instead of DB-side sort+limit (Low-Medium)

`CurrentlyPlayingRepositoryAdapter.findMostRecentByUserAndTrack` (`CurrentlyPlayingRepositoryAdapter.kt:42-63`) fetches all matching documents and does `.list().maxByOrNull { it.observedAt }` in the JVM instead of `.sort(Sorts.descending("observedAt")).limit(1)` in MongoDB. Low severity today because the collection is kept small by immediate deletion after conversion, but it's an easy fix and the same "fetch then reduce in memory" pattern is worth watching for elsewhere.

**Action:** use MongoDB sort+limit instead of loading all matches into memory.

### 7. `runBlocking` + `Dispatchers.IO` inside synchronous JAX-RS handlers (Low-Medium, coaching angle)

`DashboardService.getStats` / `getRecentlyPlayed` / `getListeningStats` / `getPlaylistCheckStats` (`DashboardService.kt:56-95, 143-153, 155-185, 187-251`) and `PlaylistCheckService.kt:88` wrap independent blocking Mongo calls in `runBlocking { async(Dispatchers.IO) { ... } }`. The intent (parallelize independent I/O) is reasonable, but doing it this way inside a request that's already running on a Quarkus worker thread adds an extra hop through the JVM-wide, size-limited `Dispatchers.IO` pool (~64 threads shared across the whole app) for no real benefit over calling Quarkus's own managed executor, or simply issuing the calls sequentially given how cheap the precomputed reads are.

At current traffic this is invisible. As concurrent dashboard/SSE usage grows, it becomes a shared, somewhat hidden point of thread-pool contention that wouldn't exist with a simpler approach. This looks like "the way it compiled" rather than a deliberate concurrency design, and is a reasonable candidate for a coaching conversation about when coroutines actually help in a blocking JAX-RS app versus adding indirection.

**Action:** either drop the coroutine wrapping in favor of plain sequential calls (all reads are against small precomputed documents), or use Quarkus's managed executor if parallelism is genuinely needed.

### 8. No pagination on catalog list ports (Low)

`AppArtistRepositoryPort.findAll()`, `AppAlbumRepositoryPort.findAll()`, `AppTrackRepositoryPort.findAll()` have no page/limit parameter, unlike `findRecentlySynced(offset, limit)` which already exists on the same adapters. Related to Finding 2 — flagged separately because it's a port-design issue independent of the specific search use case.

**Action:** remove or bound the unpaginated `findAll()` port methods once Finding 2 is fixed; there should be no unbounded read path left for a catalog that is designed to keep growing.

### 9. Per-user full reload of `currently_playing` set every poll cycle (Low)

`convertAndDeleteOrphanedItems` / `convertPartialPlays` (`PlaybackService.kt:114-149, 224-266`) reload the full currently-playing set per user every cycle rather than only new observations. Because entries are deleted immediately after conversion, this stays bounded to "currently active tracks per user" — it does not scale with history, only linearly with user count (one query per user per cycle instead of one batched query for all users).

**Action:** low priority; only worth batching into a single `$in`-based query if user count grows enough that per-cycle query volume itself becomes a concern (see Finding 4, which will bite first).

## Action Items (prioritized)

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 1 | Add index on `spotify_playlist.spotifyUserId`; replace in-memory track-count with Mongo-side `$size` aggregation | High | Small |
| 2 | Push catalog artist/album search filtering into MongoDB (regex/text index + limit) instead of `findAll()` + in-memory filter | High | Medium |
| 3 | Remove the single-user shortcut for shared catalog sync operations; add fallback across authenticated users | Medium-High | Medium |
| 4 | Confirm `to-spotify-playback` outbox partition concurrency model; plan for per-user partitioning or increased concurrency before scaling past current user count | Medium-High | Needs investigation (external library) |
| 5 | Batch the 15 sequential `findByUserAndPeriod` lookups in `StatsResource` into one query | Medium | Small |
| 6 | Replace in-memory `maxByOrNull` with Mongo `sort().limit(1)` in `CurrentlyPlayingRepositoryAdapter` | Low-Medium | Small |
| 7 | Simplify `runBlocking`/`Dispatchers.IO` usage in `DashboardService` and `PlaylistCheckService` to plain sequential calls or Quarkus's managed executor | Low-Medium | Small |
| 8 | Remove/bound unpaginated `findAll()` catalog ports once Finding 2 is fixed | Low | Small |
| 9 | Batch per-user `currently_playing` reload into a single multi-user query, if/when user count grows significantly | Low | Medium |

## Multi-user scalability — what breaks first

Ranked by how soon each mechanism is expected to become a visible problem as the user base grows from 1-2 to 20-50 users:

1. **Catalog search (Finding 2)** — grows with total catalog size, hit on every keystroke; most likely to be the first user-visible slowdown.
2. **`to-spotify-playback` partition throughput (Finding 4)** — if single-worker-per-partition, degrades silently (staler data, no errors) as user count rises.
3. **`spotify_playlist` unindexed scan (Finding 1)** — grows with total playlists × tracks across all users, hit on every settings page load.
4. **Single-user catalog-sync shortcut (Finding 3)** — not a throughput issue, but a robustness gap that becomes more likely to trigger as more users (and more chances of one stale token) are added.
5. **Per-user `currently_playing` reload (Finding 9)** — scales linearly with user count, but stays small in absolute terms until user count is quite large.

By contrast, `appendPlaybackData`'s incremental design and the precomputed aggregation pipeline are already built to scale with new data rather than total history, and should not need changes as either history or user count grows.

## On "missing coaching" as a root cause

The request explicitly asked whether missing coaching or expertise gaps — not just multi-user load — explain the performance picture here. Based on this review, the codebase does not show broad signs of inexperience: indexing, incremental processing, aggregation bounding, and Spotify batch API usage are all handled correctly in the majority of the code. The issues found are localized:

- Two clear "wrote it the straightforward way, not the scalable way" gaps (Findings 1 and 2) — both are the kind of thing a second pair of eyes or a load test against a larger dataset would have caught.
- One structural single-point-of-failure decision (Finding 3) that likely came from building for the initial single/dual-user case and not revisiting it as multi-user support was added.
- One stylistic/idiom gap around coroutine usage in blocking code (Finding 7) that is a reasonable, low-stakes coaching topic rather than a defect.

None of these indicate a need for a broad re-architecture; they're targeted fixes.
