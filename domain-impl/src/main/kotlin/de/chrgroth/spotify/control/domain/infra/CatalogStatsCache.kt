package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// shared by DomainMetrics and CatalogBrowserService so that neither the Prometheus gauges nor
// every /dashboard and /catalog page load query the catalog collections directly; a single
// refresh serves every reader, following the same pattern as OutboxPartitionStatsCache.
@ApplicationScoped
@Suppress("Unused")
class CatalogStatsCache(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
) {

  @Volatile
  private var cachedStats = CatalogStats(artistCount = 0L, albumCount = 0L, trackCount = 0L)

  fun current(): CatalogStats = cachedStats

  @Scheduled(every = "15s")
  fun refresh() {
    try {
      cachedStats = CatalogStats(
        artistCount = appArtistRepository.countAll(),
        albumCount = appAlbumRepository.countAll(),
        trackCount = appTrackRepository.countAll(),
      )
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh catalog stats cache, keeping previous values" }
    }
  }

  companion object : KLogging()
}
