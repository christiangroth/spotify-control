package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.port.out.catalog.CatalogStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs.
// counts are read from CatalogStatsPort rather than queried here, so a slow query can never delay the
// Prometheus scrape response itself. The read is shared via ScrapeSnapshot so all three gauges below report
// values from the same snapshot instead of each independently re-fetching.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class CatalogMetrics(
  private val catalogStatsPort: CatalogStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  private val snapshot = ScrapeSnapshot { catalogStatsPort.current() }

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.catalog.artists", this) { it.snapshot.current().artistCount.toDouble() }
      .description("Number of artists in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.tracks", this) { it.snapshot.current().trackCount.toDouble() }
      .description("Number of tracks in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.albums", this) { it.snapshot.current().albumCount.toDouble() }
      .description("Number of albums in the local catalog")
      .register(meterRegistry)
  }
}
