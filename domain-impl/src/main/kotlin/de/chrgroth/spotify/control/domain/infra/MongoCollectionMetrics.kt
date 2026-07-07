package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any collection activity occurs.
// collection names are read once at startup since the set of collections is fixed by the application's document model.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class MongoCollectionMetrics(
  private val mongoStats: MongoStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    mongoStats.getCollectionStats().forEach { collection ->
      Gauge.builder("mongodb.collection.size_bytes", this) { sizeForCollection(collection.name).toDouble() }
        .tag("collection", collection.name)
        .description("Size in bytes of this MongoDB collection")
        .register(meterRegistry)
    }
  }

  private fun sizeForCollection(collectionName: String): Long =
    mongoStats.getCollectionStats().firstOrNull { it.name == collectionName }?.size ?: 0L
}
