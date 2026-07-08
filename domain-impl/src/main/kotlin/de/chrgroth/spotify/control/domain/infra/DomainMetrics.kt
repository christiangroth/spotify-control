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
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

// registers eagerly on StartupEvent so these gauges are always visible, even before any sync activity occurs.
// catalog counts are refreshed on a background schedule rather than during gauge evaluation, following the same
// pattern as MongoCollectionMetrics, so a slow countAll() call can never delay the Prometheus scrape response itself.
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

  @Volatile
  private var artistCount: Long = 0L

  @Volatile
  private var trackCount: Long = 0L

  @Volatile
  private var albumCount: Long = 0L

  fun onStartup(@Observes event: StartupEvent) {
    refreshCatalogCounts()

    Gauge.builder("app.playlist.out_of_sync", this) { it.outOfSyncPlaylistCount().toDouble() }
      .description("Number of active playlists whose local mirror hasn't caught up with the latest Spotify snapshot yet")
      .register(meterRegistry)

    Gauge.builder("app.catalog.artists", this) { it.artistCount.toDouble() }
      .description("Number of artists in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.tracks", this) { it.trackCount.toDouble() }
      .description("Number of tracks in the local catalog")
      .register(meterRegistry)

    Gauge.builder("app.catalog.albums", this) { it.albumCount.toDouble() }
      .description("Number of albums in the local catalog")
      .register(meterRegistry)
  }

  @Scheduled(every = "15s")
  fun refreshCatalogCounts() {
    try {
      artistCount = appArtistRepository.countAll()
      trackCount = appTrackRepository.countAll()
      albumCount = appAlbumRepository.countAll()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh catalog counts for metrics, keeping previous values" }
    }
  }

  private fun outOfSyncPlaylistCount(): Int =
    userRepository.findAll().sumOf { user ->
      playlistRepository.findByUserId(user.spotifyUserId).count { playlist ->
        val lastSyncTime = playlist.lastSyncTime
        playlist.syncStatus == PlaylistSyncStatus.ACTIVE && (lastSyncTime == null || lastSyncTime < playlist.lastSnapshotIdSyncTime)
      }
    }

  companion object : KLogging()
}
