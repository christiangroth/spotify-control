package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.Duration
import java.time.Instant

// registers eagerly on StartupEvent so these gauges are always visible, even before any collection activity occurs.
// collection names are read once at startup since the set of collections is fixed by the application's document model.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class MongoCollectionMetrics(
  private val mongoStats: MongoStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  @Volatile
  private var cachedStats: List<MongoCollectionStats> = emptyList()

  @Volatile
  private var cachedAt: Instant = Instant.EPOCH

  fun onStartup(@Observes event: StartupEvent) {
    collectionStats().forEach { collection ->
      Gauge.builder("mongodb.collection.size_bytes", this) { sizeForCollection(collection.name).toDouble() }
        .tag("collection", collection.name)
        .description("Size in bytes of this MongoDB collection")
        .register(meterRegistry)
    }
  }

  private fun sizeForCollection(collectionName: String): Long =
    collectionStats().firstOrNull { it.name == collectionName }?.size ?: 0L

  // shares a single collStats query across all collection gauges within the same Prometheus scrape instead of
  // re-running collStats for every collection on every single gauge evaluation (previously O(collections^2) per scrape).
  private fun collectionStats(): List<MongoCollectionStats> {
    val now = Instant.now()
    if (Duration.between(cachedAt, now) > CACHE_TTL) {
      cachedStats = mongoStats.getCollectionStats()
      cachedAt = now
    }
    return cachedStats
  }

  companion object {
    private val CACHE_TTL = Duration.ofSeconds(5)
  }
}
