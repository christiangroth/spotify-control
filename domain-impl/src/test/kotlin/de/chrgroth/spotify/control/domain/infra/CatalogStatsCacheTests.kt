package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogStatsCacheTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val cache = CatalogStatsCache(appArtistRepository, appAlbumRepository, appTrackRepository)

  private val assumptionStatuses = setOf(ArtistSyncStatus.SYNC_ASSUMPTION, ArtistSyncStatus.SHALLOW_ASSUMPTION)
  private val shallowStatuses = setOf(ArtistSyncStatus.SHALLOW)

  @Test
  fun `current is zeroed out before the first refresh`() {
    assertThat(cache.current()).isEqualTo(CatalogStats(artistCount = 0L, albumCount = 0L, trackCount = 0L))
  }

  @Test
  fun `refresh populates current with the latest catalog counts`() {
    every { appArtistRepository.countAll() } returns 3L
    every { appAlbumRepository.countAll() } returns 5L
    every { appTrackRepository.countAll() } returns 42L
    every { appArtistRepository.countByStatuses(assumptionStatuses) } returns 2L
    every { appArtistRepository.countByStatuses(shallowStatuses) } returns 1L

    cache.refresh()

    assertThat(cache.current()).isEqualTo(
      CatalogStats(artistCount = 3L, albumCount = 5L, trackCount = 42L, undecidedArtistCount = 2L, shallowArtistCount = 1L),
    )
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    every { appArtistRepository.countAll() } returns 3L
    every { appAlbumRepository.countAll() } returns 5L
    every { appTrackRepository.countAll() } returns 42L
    every { appArtistRepository.countByStatuses(assumptionStatuses) } returns 2L
    every { appArtistRepository.countByStatuses(shallowStatuses) } returns 1L
    cache.refresh()

    every { appArtistRepository.countAll() } throws IllegalStateException("mongo unreachable")
    cache.refresh()

    assertThat(cache.current()).isEqualTo(
      CatalogStats(artistCount = 3L, albumCount = 5L, trackCount = 42L, undecidedArtistCount = 2L, shallowArtistCount = 1L),
    )
  }

  @Test
  fun `current can be read repeatedly without re-querying the catalog repositories`() {
    every { appArtistRepository.countAll() } returns 0L
    every { appAlbumRepository.countAll() } returns 0L
    every { appTrackRepository.countAll() } returns 0L
    every { appArtistRepository.countByStatuses(assumptionStatuses) } returns 0L
    every { appArtistRepository.countByStatuses(shallowStatuses) } returns 0L
    cache.refresh()

    repeat(5) { cache.current() }

    verify(exactly = 1) { appArtistRepository.countAll() }
  }
}
