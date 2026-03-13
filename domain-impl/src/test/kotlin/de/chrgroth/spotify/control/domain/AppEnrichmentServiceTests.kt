package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class AppEnrichmentServiceTests {

    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val syncPoolRepository: AppSyncPoolRepositoryPort = mockk()

    private val service = AppSyncService(appArtistRepository, appTrackRepository, syncPoolRepository)

    private val userId = UserId("user-1")

    private val artist1 = AppArtist(artistId = "artist-1", artistName = "Artist One")
    private val track1 = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"))

    @Test
    fun `does nothing when both lists are empty`() {
        service.upsertAndAddToSyncPool(emptyList(), emptyList())

        verify(exactly = 0) { appArtistRepository.upsertAll(any()) }
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `upserts artists and adds to sync pool`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs

        service.upsertAndAddToSyncPool(listOf(artist1), emptyList())

        verify { appArtistRepository.upsertAll(listOf(artist1)) }
        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
    }

    @Test
    fun `upserts tracks and adds to sync pool`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        service.upsertAndAddToSyncPool(emptyList(), listOf(track1))

        verify { appTrackRepository.upsertAll(listOf(track1)) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `does not add to sync pool when track has albumId`() {
        val trackWithAlbum = track1.copy(albumId = AlbumId("album-1"))
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        service.upsertAndAddToSyncPool(emptyList(), listOf(trackWithAlbum))

        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `adds both artists and tracks to sync pool`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        service.upsertAndAddToSyncPool(listOf(artist1), listOf(track1))

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }
}
