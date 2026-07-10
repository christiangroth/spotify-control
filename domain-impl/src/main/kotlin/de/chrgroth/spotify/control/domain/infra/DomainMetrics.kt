package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs.
// catalog counts are read from the shared CatalogStatsCache rather than queried here, so a slow countAll() call
// can never delay the Prometheus scrape response itself, and the same cache also serves CatalogBrowserService.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class DomainMetrics(
  private val playlistRepository: PlaylistRepositoryPort,
  private val catalogStatsCache: CatalogStatsCache,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.out_of_sync", this) { it.outOfSyncPlaylistCount().toDouble() }
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

  private fun outOfSyncPlaylistCount(): Int =
    playlistRepository.findAll().count { playlist ->
      val lastSyncTime = playlist.lastSyncTime
      playlist.syncStatus == PlaylistSyncStatus.ACTIVE && (lastSyncTime == null || lastSyncTime < playlist.lastSnapshotIdSyncTime)
    }

  companion object : KLogging()
}
