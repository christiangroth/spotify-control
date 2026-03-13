package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class AppEnrichmentServiceTests {

    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()

    private val service = AppEnrichmentService(appArtistRepository, appTrackRepository, outboxPort)

    private val userId = UserId("user-1")

    private val artist1 = AppArtist(artistId = "artist-1", artistName = "Artist One")
    private val track1 = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"))

    @Test
    fun `does nothing when both lists are empty`() {
        service.upsertAndEnqueueEnrichment(emptyList(), emptyList(), userId)

        verify(exactly = 0) { appArtistRepository.upsertAll(any()) }
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `upserts artists and enqueues EnrichArtistDetails`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.upsertAndEnqueueEnrichment(listOf(artist1), emptyList(), userId)

        verify { appArtistRepository.upsertAll(listOf(artist1)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `upserts tracks and enqueues EnrichTrackDetails`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.upsertAndEnqueueEnrichment(emptyList(), listOf(track1), userId)

        verify { appTrackRepository.upsertAll(listOf(track1)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichTrackDetails("track-1", userId)) }
    }

    @Test
    fun `does not enqueue EnrichAlbumDetails even when track has albumId`() {
        val trackWithAlbum = track1.copy(albumId = AlbumId("album-1"))
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.upsertAndEnqueueEnrichment(emptyList(), listOf(trackWithAlbum), userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichTrackDetails("track-1", userId)) }
        verify(exactly = 0) { outboxPort.enqueue(match { it.key == "EnrichAlbumDetails" }) }
    }

    @Test
    fun `enqueues both artist and track enrichment events`() {
        every { appArtistRepository.upsertAll(any()) } just runs
        every { appTrackRepository.upsertAll(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.upsertAndEnqueueEnrichment(listOf(artist1), listOf(track1), userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-1", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichTrackDetails("track-1", userId)) }
    }
}
