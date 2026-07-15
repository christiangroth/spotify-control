package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.CatalogStatsPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// implements CatalogStatsPort so adapter-in-http-metrics (which only depends on domain-api) can register
// gauges from these counts without querying the catalog collections directly, following the same pattern
// as PlaylistStatsCache. Also shared directly with CatalogBrowserService so every /dashboard and /catalog
// page load avoids querying the catalog collections too; a single refresh serves every reader, following
// the same pattern as OutboxPartitionStatsCache. Delayed against the other every="15s" caches so they
// don't all fire against MongoDB in the same tick.
@ApplicationScoped
@Suppress("Unused")
class CatalogStatsCache(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
) : CatalogStatsPort {

  @Volatile
  private var cachedStats = CatalogStats(artistCount = 0L, albumCount = 0L, trackCount = 0L)

  override fun current(): CatalogStats = cachedStats

  @Scheduled(every = "15s", delayed = "8s")
  fun refresh() {
    try {
      cachedStats = CatalogStats(
        artistCount = appArtistRepository.countAll(),
        albumCount = appAlbumRepository.countAll(),
        trackCount = appTrackRepository.countAll(),
        undecidedArtistCount = appArtistRepository.countByStatuses(ASSUMPTION_STATUSES),
      )
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh catalog stats cache, keeping previous values" }
    }
  }

  companion object : KLogging() {
    private val ASSUMPTION_STATUSES = setOf(ArtistSyncStatus.SYNC_ASSUMPTION, ArtistSyncStatus.SHALLOW_ASSUMPTION)
  }
}
