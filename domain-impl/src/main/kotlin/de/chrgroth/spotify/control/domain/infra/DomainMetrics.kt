package de.chrgroth.spotify.control.domain.infra

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs.
// catalog and playlist counts are read from the shared CatalogStatsCache/PlaylistStatsCache rather than queried
// here, so a slow query can never delay the Prometheus scrape response itself.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class DomainMetrics(
  private val playlistStatsCache: PlaylistStatsCache,
  private val catalogStatsCache: CatalogStatsCache,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.out_of_sync", this) { it.playlistStatsCache.current().outOfSyncCount.toDouble() }
      .description("Number of active playlists whose local mirror hasn't caught up with the latest Spotify snapshot yet")
      .register(meterRegistry)

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
