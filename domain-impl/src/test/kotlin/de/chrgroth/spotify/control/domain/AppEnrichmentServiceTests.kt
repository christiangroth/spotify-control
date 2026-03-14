package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class AppEnrichmentServiceTests {

    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val syncPoolRepository: AppSyncPoolRepositoryPort = mockk()

    private val service = AppSyncService(appArtistRepository, appTrackRepository, syncPoolRepository)

    @Test
    fun `does nothing when both lists are empty`() {
        service.addToSyncPool(emptyList(), emptyList())

        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `adds artist ID to sync pool when artist not yet in catalog`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs

        service.addToSyncPool(listOf("artist-1"), emptyList())

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
    }

    @Test
    fun `skips artist sync pool when artist already in catalog`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(
            de.chrgroth.spotify.control.domain.model.AppArtist(
                artistId = "artist-1",
                artistName = "Artist One",
                genre = "rock",
                lastSync = Instant.fromEpochSeconds(1),
            )
        )

        service.addToSyncPool(listOf("artist-1"), emptyList())

        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
    }

    @Test
    fun `adds track ID to sync pool when track not yet in catalog`() {
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns emptyList()
        every { syncPoolRepository.addTracks(any()) } just runs

        service.addToSyncPool(emptyList(), listOf("track-1"))

        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `skips track sync pool when track already in catalog`() {
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(
            de.chrgroth.spotify.control.domain.model.AppTrack(
                id = TrackId("track-1"),
                title = "Track One",
                artistId = de.chrgroth.spotify.control.domain.model.ArtistId("artist-1"),
                albumId = de.chrgroth.spotify.control.domain.model.AlbumId("album-1"),
                lastSync = Instant.fromEpochSeconds(1),
            )
        )

        service.addToSyncPool(emptyList(), listOf("track-1"))

        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `adds both artists and tracks to sync pool when not yet in catalog`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns emptyList()
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        service.addToSyncPool(listOf("artist-1"), listOf("track-1"))

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `force sync adds all IDs to pool even when already in catalog`() {
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        service.addToSyncPool(listOf("artist-1"), listOf("track-1"), forceSync = true)

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
        verify(exactly = 0) { appArtistRepository.findByArtistIds(any()) }
        verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
    }
}
