package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class PromoteAlreadySyncedAssumptionArtistsStarterTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()

  private val starter = PromoteAlreadySyncedAssumptionArtistsStarter(appArtistRepository, appAlbumRepository)

  private val syncTimestamp = Instant.fromEpochSeconds(0)

  private fun artist(id: String, status: ArtistSyncStatus) = AppArtist(
    id = ArtistId(id),
    artistName = "Artist $id",
    lastSync = syncTimestamp,
    syncStatus = status,
  )

  @Test
  fun `promotes already synced assumption artists to their definitive status`() {
    val syncAssumptionWithAlbums = artist("artist-1", ArtistSyncStatus.SYNC_ASSUMPTION)
    val shallowAssumptionWithAlbums = artist("artist-2", ArtistSyncStatus.SHALLOW_ASSUMPTION)
    val syncAssumptionWithoutAlbums = artist("artist-3", ArtistSyncStatus.SYNC_ASSUMPTION)
    val alreadySynced = artist("artist-4", ArtistSyncStatus.SYNC)
    every { appArtistRepository.findAll() } returns listOf(
      syncAssumptionWithAlbums, shallowAssumptionWithAlbums, syncAssumptionWithoutAlbums, alreadySynced,
    )
    every {
      appAlbumRepository.countByArtistIds(setOf(ArtistId("artist-1"), ArtistId("artist-2"), ArtistId("artist-3")))
    } returns mapOf("artist-1" to 5L, "artist-2" to 3L)
    every { appArtistRepository.setSyncStatus(any(), any()) } just runs

    starter.execute()

    verify { appArtistRepository.setSyncStatus(ArtistId("artist-1"), ArtistSyncStatus.SYNC) }
    verify { appArtistRepository.setSyncStatus(ArtistId("artist-2"), ArtistSyncStatus.SHALLOW) }
    verify(exactly = 0) { appArtistRepository.setSyncStatus(ArtistId("artist-3"), any()) }
    verify(exactly = 0) { appArtistRepository.setSyncStatus(ArtistId("artist-4"), any()) }
  }

  @Test
  fun `does nothing when there are no assumption status artists`() {
    every { appArtistRepository.findAll() } returns listOf(artist("artist-1", ArtistSyncStatus.SYNC))

    starter.execute()

    verify(exactly = 0) { appAlbumRepository.countByArtistIds(any()) }
    verify(exactly = 0) { appArtistRepository.setSyncStatus(any(), any()) }
  }
}
