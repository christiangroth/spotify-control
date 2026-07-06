package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class DomainMetrics(
  private val userRepository: UserRepositoryPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.out_of_sync", this) { it.outOfSyncPlaylistCount().toDouble() }
      .description("Number of active playlists whose local mirror hasn't caught up with the latest Spotify snapshot yet")
      .register(meterRegistry)

    Gauge.builder("app.catalog.artists", this) { appArtistRepository.countAll().toDouble() }
      .description("Number of artists in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.tracks", this) { appTrackRepository.countAll().toDouble() }
      .description("Number of tracks in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.albums", this) { appAlbumRepository.countAll().toDouble() }
      .description("Number of albums in the local catalog")
      .register(meterRegistry)
  }

  private fun outOfSyncPlaylistCount(): Int =
    userRepository.findAll().sumOf { user ->
      playlistRepository.findByUserId(user.spotifyUserId).count { playlist ->
        val lastSyncTime = playlist.lastSyncTime
        playlist.syncStatus == PlaylistSyncStatus.ACTIVE && (lastSyncTime == null || lastSyncTime < playlist.lastSnapshotIdSyncTime)
      }
    }
}
