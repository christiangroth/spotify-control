package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import java.time.Duration

// shared by metrics classes that expose several gauges derived from a single stats object (e.g. PlaylistMetrics,
// CatalogMetrics), so that all gauges of one scrape report values from the same underlying read instead of each
// gauge independently re-fetching. The TTL is generous relative to a single scrape but short relative to the
// domain-side cache refresh interval, so gauges stay effectively live.
internal class ScrapeSnapshot<T>(
  private val ttl: Duration = Duration.ofSeconds(1),
  private val fetch: () -> T,
) {

  @Volatile
  private var cached: T? = null

  @Volatile
  private var cachedAtNanos = 0L

  fun current(): T {
    val now = System.nanoTime()
    cached?.takeIf { now - cachedAtNanos < ttl.toNanos() }?.let { return it }
    return fetch().also {
      cached = it
      cachedAtNanos = now
    }
  }
}
