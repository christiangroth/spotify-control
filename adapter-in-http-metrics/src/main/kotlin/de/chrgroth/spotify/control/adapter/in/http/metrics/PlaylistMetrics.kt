package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any user activity occurs.
// counts are read from PlaylistStatsPort rather than queried here, so a slow query can never delay the
// Prometheus scrape response itself. The read is shared via ScrapeSnapshot so all three gauges below report
// values from the same snapshot instead of each independently re-fetching.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class PlaylistMetrics(
  private val playlistStatsPort: PlaylistStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  private val snapshot = ScrapeSnapshot { playlistStatsPort.current() }

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.tracked", this) { it.snapshot.current().trackedCount.toDouble() }
      .description("Number of playlists with sync status active")
      .register(meterRegistry)

    Gauge.builder("app.playlist.out_of_sync", this) { it.snapshot.current().outOfSyncCount.toDouble() }
      .description("Number of active playlists whose local mirror hasn't caught up with the latest Spotify snapshot yet")
      .register(meterRegistry)

    Gauge.builder("app.playlist.album_upgrade_pending", this) { it.snapshot.current().pendingAlbumUpgradeCount.toDouble() }
      .description("Number of playlist checks with a pending track-from-latest-release violation")
      .register(meterRegistry)
  }
}
