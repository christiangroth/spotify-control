package de.chrgroth.spotify.control.domain.infra

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs.
// catalog counts are read from the shared CatalogStatsCache rather than queried here, so a slow query can
// never delay the Prometheus scrape response itself.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class DomainMetrics(
  private val catalogStatsCache: CatalogStatsCache,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.catalog.artists", this) { it.catalogStatsCache.current().artistCount.toDouble() }
      .description("Number of artists in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.tracks", this) { it.catalogStatsCache.current().trackCount.toDouble() }
      .description("Number of tracks in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.albums", this) { it.catalogStatsCache.current().albumCount.toDouble() }
      .description("Number of albums in the local catalog")
      .register(meterRegistry)
  }
}
