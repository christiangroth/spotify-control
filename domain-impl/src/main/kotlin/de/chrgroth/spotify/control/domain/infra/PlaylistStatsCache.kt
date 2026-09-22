package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistStatsPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistCheckDashboardRepositoryPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// implements PlaylistStatsPort so adapter-in-http-metrics (which only depends on domain-api) can register
// gauges from these counts without querying the playlist collections directly, following the same pattern
// as CatalogStatsCache/CatalogBrowserPort. Staggered against the other every="15s" caches via delayed() so
// they don't all fire against MongoDB in the same tick. pendingAlbumUpgradeCount reads from the precomputed
// playlist-check dashboard read model (see #867 Slow Queries, ADR-0014) instead of AppPlaylistCheckRepositoryPort.findAll()
// directly, which was an unfiltered full-collection scan running every 15s (~1.1s per call in production).
@ApplicationScoped
@Suppress("Unused")
class PlaylistStatsCache(
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckDashboardRepository: PlaylistCheckDashboardRepositoryPort,
) : PlaylistStatsPort {

  @Volatile
  private var cachedStats = PlaylistStats()

  override fun current(): PlaylistStats = cachedStats

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
        pendingAlbumUpgradeCount = playlistCheckDashboardRepository.find()?.checks
          .orEmpty()
          .count { !it.succeeded && it.checkId.endsWith(":$ALBUM_UPGRADE_CHECK_ID") },
      )
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh playlist stats cache, keeping previous values" }
    }
  }

  companion object : KLogging() {
    private const val ALBUM_UPGRADE_CHECK_ID = "track-from-latest-release"
  }
}
