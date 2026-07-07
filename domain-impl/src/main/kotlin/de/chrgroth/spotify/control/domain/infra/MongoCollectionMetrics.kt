package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

// registers eagerly on StartupEvent so these gauges are always visible, even before any collection activity occurs.
// collection names are read once at startup since the set of collections is fixed by the application's document model.
// stats are refreshed on a background schedule rather than during gauge evaluation, so a slow/blocking collStats
// call can never delay the Prometheus scrape response itself (a scrape timeout drops the whole /q/metrics scrape,
// not just the affected gauges).
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class MongoCollectionMetrics(
  private val mongoStats: MongoStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  @Volatile
  private var cachedStats: List<MongoCollectionStats> = emptyList()

  fun onStartup(@Observes event: StartupEvent) {
    refresh()
    cachedStats.forEach { collection ->
      Gauge.builder("mongodb.collection.size_bytes", this) { sizeForCollection(collection.name).toDouble() }
        .tag("collection", collection.name)
        .description("Size in bytes of this MongoDB collection")
        .register(meterRegistry)
    }
  }

  @Scheduled(every = "15s")
  fun refresh() {
    try {
      cachedStats = mongoStats.getCollectionStats()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh MongoDB collection stats for metrics, keeping previous values" }
    }
  }

  private fun sizeForCollection(collectionName: String): Long =
    cachedStats.firstOrNull { it.name == collectionName }?.size ?: 0L

  companion object : KLogging()
}
