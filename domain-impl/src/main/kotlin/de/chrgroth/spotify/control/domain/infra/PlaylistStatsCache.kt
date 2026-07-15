package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistStatsPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// implements PlaylistStatsPort so adapter-in-http-metrics (which only depends on domain-api) can register
// gauges from these counts without querying the playlist collections directly, following the same pattern
// as CatalogStatsCache/CatalogBrowserPort. Staggered against the other every="15s" caches via delayed() so
// they don't all fire against MongoDB in the same tick.
@ApplicationScoped
@Suppress("Unused")
class PlaylistStatsCache(
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
) : PlaylistStatsPort {

  @Volatile
  private var cachedStats = PlaylistStats()

  override fun getPlaylistStats(): PlaylistStats = cachedStats

  @Scheduled(every = "15s", delayed = "4s")
  fun refresh() {
    try {
      val playlists = playlistRepository.findAll()
      cachedStats = PlaylistStats(
        trackedCount = playlists.count { it.syncStatus == PlaylistSyncStatus.ACTIVE },
        outOfSyncCount = playlists.count { playlist ->
          val lastSyncTime = playlist.lastSyncTime
          playlist.syncStatus == PlaylistSyncStatus.ACTIVE && (lastSyncTime == null || lastSyncTime < playlist.lastSnapshotIdSyncTime)
        },
        pendingAlbumUpgradeCount = playlistCheckRepository.findAll().count { !it.succeeded && it.checkId.endsWith(":$ALBUM_UPGRADE_CHECK_ID") },
      )
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh playlist stats cache, keeping previous values" }
    }
  }

  companion object : KLogging() {
    private const val ALBUM_UPGRADE_CHECK_ID = "track-from-latest-release"
  }
}
