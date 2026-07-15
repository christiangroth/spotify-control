package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.port.out.infra.MongoCollectionStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any collection activity occurs.
// collection names are read once at startup since the set of collections is fixed by the application's document model.
// stats are cached and refreshed on a background schedule by MongoCollectionStatsPort's implementation
// (MongoStatsAdapter), shared with every other reader (e.g. HealthService), so a slow/blocking collStats call can
// never delay the Prometheus scrape response itself, and MongoDB is never queried more than once per refresh cycle.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class MongoCollectionMetrics(
  private val mongoCollectionStats: MongoCollectionStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    mongoCollectionStats.current().forEach { collection ->
      Gauge.builder("mongodb.collection.size_bytes", this) { sizeForCollection(collection.name).toDouble() }
        .tag("collection", collection.name)
        .description("Size in bytes of this MongoDB collection")
        .register(meterRegistry)
    }
  }

  private fun sizeForCollection(collectionName: String): Long =
    mongoCollectionStats.current().firstOrNull { it.name == collectionName }?.size ?: 0L
}
