package de.chrgroth.spotify.control.domain.infra

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any user activity occurs.
// counts are read from the shared PlaylistStatsCache rather than queried here, so a slow query can never delay
// the Prometheus scrape response itself.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OverviewMetrics(
  private val playlistStatsCache: PlaylistStatsCache,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.tracked", this) { it.playlistStatsCache.current().trackedCount.toDouble() }
      .description("Number of playlists with sync status active")
      .register(meterRegistry)

    Gauge.builder("app.playlist.album_upgrade_pending", this) { it.playlistStatsCache.current().pendingAlbumUpgradeCount.toDouble() }
      .description("Number of playlist checks with a pending track-from-latest-release violation")
      .register(meterRegistry)
  }
}
