# Performance Review — 2026-07-10

## Context

This review supersedes the previous performance review (formerly
`docs/reviews/2026-07-08-performance-review.md`, now removed). Since that review, the application
was rebuilt for exactly one user (see [ADR-0008](../adr/0008-single-user-architecture.md), #749).
Several findings from the old review were multi-user-scaling concerns that are now moot — a
`spotify_playlist` collection sized to one person's own playlists, or a `findTrackCounts` scan
bounded by that same collection, will never grow to a size where an index would matter.

The old review's caching-related findings (metrics gauges hitting MongoDB inside the Prometheus
scrape, `CatalogBrowserService` loading whole collections into memory) have also already been
fixed in the meantime — `DomainMetrics` now uses the same scheduled-refresh cache pattern as
`MongoCollectionMetrics`/`OutboxPartitionStatsCache`, and `CatalogBrowserService.getArtists`/
`getAlbums` are bounded, filter-driven searches rather than full-collection loads.

**What single-user does *not* remove as a performance axis:** the local catalog
(`app_artist`/`app_track`/`app_album`) and playback history (`app_playback`,
`app_playback_aggregation`) grow with *listening history over years*, independent of user count.
That's where this review focuses, alongside the caching gaps explicitly asked about.

## What's already solid

- **Deliberate indexes for the hot paths** — `MongoIndexInitializer` creates compound indexes for
  playback lookups, catalog sync-pool queries, and playlist sync status.
- **Single-user identity is cached** — `CurrentUserResolver` resolves the one stored user's ID once
  and caches it in a `@Volatile` field for the life of the bean (`domain-impl/.../user/CurrentUserResolver.kt:15-24`),
  with a code comment explaining why this is safe for a single-user app.
- **Metrics gauges don't block Prometheus scrapes** — `DomainMetrics`, `MongoCollectionMetrics`,
  and `OutboxPartitionStatsCache` all refresh their counts on a `@Scheduled(every = "15s")`
  background job into `@Volatile` fields, rather than querying MongoDB inside the gauge callback.
- **Catalog browsing is bounded** — `CatalogBrowserService.getArtists`/`getAlbums`
  (`domain-impl/.../catalog/CatalogBrowserService.kt:47-92`) only run when a filter is present and
  cap results at `SEARCH_RESULT_LIMIT = 50`.
- **Dashboard aggregation runs queries in parallel** — `DashboardService.getStats()`
  (`domain-impl/.../infra/DashboardService.kt:55-79`) fires off independent MongoDB reads via
  `ManagedExecutor.supplyAsync` instead of running them sequentially.
- **Incremental playback processing** — `PlaybackService.appendPlaybackDataForUser()`
  (`domain-impl/.../playback/PlaybackService.kt:282-308`) only processes items newer than the most
  recent stored `app_playback` entry; it never re-scans full history except on an explicit rebuild.
- **Bulk writes, not per-item upserts** — playback and playlist sync paths use `saveAll`/
  `persist(documents)` for batches instead of looping single-document upserts.
- **Query-level observability already exists** — every repository call in `adapter-out-mongodb` is
  wrapped in `mongoQueryMetrics.timed(...)`, so slow queries are already measurable without adding
  new instrumentation.

## Findings

### 1. Catalog search does a full, unindexed collection scan (Medium)

`AppArtistRepositoryAdapter.searchByName` and `AppAlbumRepositoryAdapter.searchByTitle` build the
search filter as an unanchored, case-insensitive regex:

```kotlin
// adapter-out-mongodb/.../AppArtistRepositoryAdapter.kt:66-73
override fun searchByName(filter: String, limit: Int): List<AppArtist> =
  mongoQueryMetrics.timed("app_artist.searchByName") {
    appArtistDocumentRepository.mongoCollection()
      .find(Filters.regex(ARTIST_NAME_FIELD, Pattern.quote(filter), "i"))
      .limit(limit)
      ...
```

```kotlin
// adapter-out-mongodb/.../AppAlbumRepositoryAdapter.kt:73-79
override fun searchByTitle(filter: String, limit: Int): List<AppAlbum> =
  ...find(Filters.regex(TITLE_FIELD, Pattern.quote(filter), "i")).limit(limit)...
```

Because the pattern isn't prefix-anchored (`^...`), MongoDB cannot use an index for this query even
if one existed — every keystroke in the catalog search box triggers a full `COLLSCAN` with a
regex evaluated against every document. `MongoIndexInitializer` does not define any index on
`artistName` or `title` either. For a single, long-lived listening history this collection grows
unboundedly over the years (every artist/album ever played or in a synced playlist), so search
latency degrades quietly over time rather than failing outright.

**Suggested fix:** at minimum, add a plain index on `artistName`/`title` so short-circuiting
(`limit`, `count`) is cheaper, and consider a real substring-search strategy if the catalog grows
large — e.g. a text index with `$text` search, or restricting the UI to prefix search (`^filter`)
which *can* use a standard index.

### 2. `CatalogBrowserService.getCatalogStats()` is uncached and duplicates work `DomainMetrics` already does (Medium)

```kotlin
// domain-impl/.../catalog/CatalogBrowserService.kt:36-45
override fun getCatalogStats(): CatalogStats {
  val artistCount = appArtistRepository.countAll()
  val albumCount = appAlbumRepository.countAll()
  val trackCount = appTrackRepository.countAll()
  ...
```

This runs live on every `/dashboard` load (`DashboardResource.dashboard()` →
`DashboardService.getStats()`, `DashboardService.kt:62`) and every `/catalog` load
(`CatalogResource.catalog()`, `adapter-in-web/.../CatalogResource.kt:41`) — three uncached
`countAll()` calls per page view.

Meanwhile `DomainMetrics.refreshCatalogCounts()` (`domain-impl/.../infra/DomainMetrics.kt:58-67`)
already computes the *exact same three counts* on a 15-second schedule into `@Volatile` fields, for
Prometheus. Two independent code paths query the same three collections for the same numbers
instead of one shared cache serving both.

**Suggested fix:** expose `DomainMetrics`' cached counts (or extract a small shared
`CatalogStatsCache`, following the same pattern as `MongoCollectionMetrics`/
`OutboxPartitionStatsCache`) and have `CatalogBrowserService.getCatalogStats()` read from it instead
of hitting MongoDB directly on every page view.

### 3. `UserProfileService.getDisplayName()` bypasses the caching pattern the codebase already established (Low-Medium)

```kotlin
// domain-impl/.../user/UserProfileService.kt:24
override fun getDisplayName(): String? = userRepository.get()?.displayName
```

Every authenticated page load calls this — `DashboardResource`, `PlaybackSettingsResource`,
`PlaylistSettingsResource`, `PlaylistsResource`, and `PlaybackResource` all call
`userProfile.getDisplayName()` once per request to render the header. Each call is a fresh
`userRepository.get()` MongoDB round-trip (`UserRepositoryAdapter.get()`:
`userDocumentRepository.listAll().firstOrNull()`).

This is the same repository `CurrentUserResolver` already wraps with a `@Volatile`-cached lookup,
on the explicit reasoning that a single-user app's stored user data is stable for the life of the
process. `displayName` only changes via the daily `UpdateUserProfile` scheduled job
(`UserProfileService.update()`), so it is exactly the kind of value that pattern was designed for —
it just wasn't applied here.

**Suggested fix:** cache the resolved `User` (or at least `displayName`) the same way
`CurrentUserResolver` caches the user ID, and invalidate/update the cached value in
`UserProfileService.update()` when the display name actually changes.

### 4. `sumEventCount()` sums in application code instead of in MongoDB (Low)

```kotlin
// adapter-out-mongodb/.../PlaybackAggregationRepositoryAdapter.kt:83-90
override fun sumEventCount(): Long =
  mongoQueryMetrics.timed("app_playback_aggregation.sumEventCount") {
    repository.mongoCollection()
      .find(Filters.eq(TYPE_FIELD, AggregationPeriodType.DAY.name))
      .projection(Projections.include(EVENT_COUNT_FIELD))
      .toList()
      .sumOf { it.eventCount }
  }
```

Called on every dashboard load (`DashboardService.getStats()`/`getPlaybackStats()`), this pulls
every `DAY` aggregation document — one per day since the account started using the app — into the
JVM and sums them in Kotlin, rather than letting MongoDB do the sum server-side with a `$group`
aggregation pipeline (the same technique `PlaylistRepositoryAdapter.findTrackCounts()` already uses
elsewhere in this same adapter package via `Aggregates.project`). At one document per day this is
small for years to come, but it's needless client-side work that grows linearly forever, and it's
an inconsistent pattern next to the aggregation pipeline already used nearby.

**Suggested fix:** replace with a `$group`/`$sum` aggregation pipeline so MongoDB returns a single
number.

## Action items

| Priority | Finding | File | Fix |
|----------|---------|------|-----|
| 1 | Catalog search full collection scan | `AppArtistRepositoryAdapter.kt:66`, `AppAlbumRepositoryAdapter.kt:73` | Add index on `artistName`/`title`; consider `$text` search or prefix-anchored search for large catalogs |
| 2 | `getCatalogStats()` uncached + duplicated | `CatalogBrowserService.kt:36`, `DomainMetrics.kt:58` | Share `DomainMetrics`' scheduled cache instead of a second live `countAll()` path |
| 3 | `getDisplayName()` bypasses established cache pattern | `UserProfileService.kt:24` | Cache the resolved user/display name like `CurrentUserResolver` does |
| 4 | `sumEventCount()` sums client-side | `PlaybackAggregationRepositoryAdapter.kt:83` | Use a MongoDB `$group`/`$sum` aggregation pipeline |

## On "missing coaching"

The single-user rebuild already applied the two caching lessons from the previous review
consistently in most places — `CurrentUserResolver`, `DomainMetrics`, `MongoCollectionMetrics`, and
`OutboxPartitionStatsCache` all follow the same scheduled-refresh-into-`@Volatile`-field pattern.
The gaps found here (findings 2 and 3) are exactly that pattern *not yet* applied to two remaining
call sites, not a new or systemic issue — the fix is to extend a pattern that's already proven out
elsewhere in the codebase, not to introduce a new caching mechanism (e.g. `quarkus-cache`, which
this project does not currently depend on and doesn't need to for a single-user workload this
small).
