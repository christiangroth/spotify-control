package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any user activity occurs
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OverviewMetrics(
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.tracked", this) { it.trackedPlaylistCount().toDouble() }
      .description("Number of playlists with sync status active")
      .register(meterRegistry)

    Gauge.builder("app.playlist.album_upgrade_pending", this) { it.pendingAlbumUpgradeCount().toDouble() }
      .description("Number of playlist checks with a pending track-from-latest-release violation")
      .register(meterRegistry)
  }

  private fun trackedPlaylistCount(): Int =
    playlistRepository.findAll().count { it.syncStatus == PlaylistSyncStatus.ACTIVE }

  private fun pendingAlbumUpgradeCount(): Int =
    playlistCheckRepository.findAll().count { !it.succeeded && it.checkId.endsWith(":$ALBUM_UPGRADE_CHECK_ID") }

  companion object {
    private const val ALBUM_UPGRADE_CHECK_ID = "track-from-latest-release"
  }
}
