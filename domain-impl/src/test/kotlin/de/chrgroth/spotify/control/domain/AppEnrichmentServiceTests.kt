package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UseBulkFetchStatePort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
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
    private val outboxPort: OutboxPort = mockk()
    private val useBulkFetchState: UseBulkFetchStatePort = mockk()
    private val userRepository: UserRepositoryPort = mockk()

    private val service = AppSyncService(
        appArtistRepository, appTrackRepository, syncPoolRepository,
        outboxPort, useBulkFetchState, userRepository,
    )

    private val userId = UserId("user-1")
    private fun buildUser(id: String = "user-1") = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Instant.DISTANT_FUTURE,
        lastLoginAt = Instant.fromEpochSeconds(0),
    )

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
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekArtists(any()) } returns emptyList()

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
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekTracks(any()) } returns emptyList()

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
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekArtists(any()) } returns emptyList()
        every { syncPoolRepository.peekTracks(any()) } returns emptyList()

        service.addToSyncPool(listOf("artist-1"), listOf("track-1"))

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `force sync adds all IDs to pool even when already in catalog`() {
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekArtists(any()) } returns emptyList()
        every { syncPoolRepository.peekTracks(any()) } returns emptyList()

        service.addToSyncPool(listOf("artist-1"), listOf("track-1"), forceSync = true)

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
        verify(exactly = 0) { appArtistRepository.findByArtistIds(any()) }
        verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
    }

    @Test
    fun `enqueues SyncMissingArtists bulk event when artists added and bulk fetch enabled`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekArtists(50) } returnsMany listOf(listOf("artist-1"), emptyList())
        every { syncPoolRepository.markArtistsEnqueued(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.addToSyncPool(listOf("artist-1"), emptyList())

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncMissingArtists(listOf("artist-1"))) }
        verify { syncPoolRepository.markArtistsEnqueued(listOf("artist-1")) }
    }

    @Test
    fun `enqueues per-item SyncArtistDetails when artists added and bulk fetch disabled`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs
        every { useBulkFetchState.isUsingBulkFetch() } returns false
        every { userRepository.findAll() } returns listOf(buildUser())
        every { syncPoolRepository.peekArtists(50) } returnsMany listOf(listOf("artist-1"), emptyList())
        every { syncPoolRepository.markArtistsEnqueued(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.addToSyncPool(listOf("artist-1"), emptyList())

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
        verify { syncPoolRepository.markArtistsEnqueued(listOf("artist-1")) }
    }

    @Test
    fun `enqueues SyncMissingTracks bulk event when tracks added and bulk fetch enabled`() {
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns emptyList()
        every { syncPoolRepository.addTracks(any()) } just runs
        every { useBulkFetchState.isUsingBulkFetch() } returns true
        every { syncPoolRepository.peekTracks(50) } returnsMany listOf(listOf("track-1"), emptyList())
        every { syncPoolRepository.markTracksEnqueued(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.addToSyncPool(emptyList(), listOf("track-1"))

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncMissingTracks(listOf("track-1"))) }
        verify { syncPoolRepository.markTracksEnqueued(listOf("track-1")) }
    }

    @Test
    fun `addAlbumsToSyncPool adds albums and enqueues SyncMissingAlbums events`() {
        every { syncPoolRepository.addAlbums(any()) } just runs
        every { syncPoolRepository.peekAlbums(50) } returnsMany listOf(listOf("album-1"), emptyList())
        every { syncPoolRepository.markAlbumsEnqueued(any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        service.addAlbumsToSyncPool(listOf("album-1"))

        verify { syncPoolRepository.addAlbums(listOf("album-1")) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncMissingAlbums("album-1")) }
        verify { syncPoolRepository.markAlbumsEnqueued(listOf("album-1")) }
    }

    @Test
    fun `addAlbumsToSyncPool does nothing when list is empty`() {
        service.addAlbumsToSyncPool(emptyList())

        verify(exactly = 0) { syncPoolRepository.addAlbums(any()) }
        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }
}
