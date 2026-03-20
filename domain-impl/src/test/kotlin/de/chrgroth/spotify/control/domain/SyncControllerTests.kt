package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class SyncControllerTests {

    private val appTrackRepository: AppTrackRepositoryPort = mockk(relaxed = true)
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk(relaxed = true)
    private val appArtistRepository: AppArtistRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk(relaxed = true)

    private val controller = SyncController(
        appTrackRepository,
        appAlbumRepository,
        appArtistRepository,
        outboxPort,
    )

    private val userId = UserId("user-1")
    private val syncTime = Instant.fromEpochSeconds(1)

    private fun track(id: String) = AppTrack(
        id = TrackId(id),
        title = "Track $id",
        artistId = ArtistId("artist-$id"),
        lastSync = syncTime,
    )

    private fun album(id: String) = AppAlbum(id = AlbumId(id), lastSync = syncTime)

    private fun artist(id: String) = AppArtist(
        artistId = id,
        artistName = "Artist $id",
        playbackProcessingStatus = ArtistPlaybackProcessingStatus.UNDECIDED,
        lastSync = syncTime,
    )

    // --- syncForTracks ---

    @Test
    fun `syncForTracks with empty list does nothing`() {
        controller.syncForTracks(emptyList(), userId)

        verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `syncForTracks skips tracks already present in catalog`() {
        every { appTrackRepository.findByTrackIds(any()) } returns listOf(track("t1"))

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", "album-1", listOf("artist-1"))),
            userId,
        )

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `syncForTracks enqueues SyncAlbumDetails for missing track's album`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", "album-1", listOf("artist-1"))),
            userId,
        )

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-1")) }
    }

    @Test
    fun `syncForTracks does not enqueue SyncAlbumDetails when album already in catalog`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns listOf(album("album-1"))
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", "album-1", listOf("artist-1"))),
            userId,
        )

        verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncAlbumDetails }) }
    }

    @Test
    fun `syncForTracks enqueues SyncArtistDetails via album sync for missing tracks`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", "album-1", listOf("artist-1"))),
            userId,
        )

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `syncForTracks does not enqueue SyncArtistDetails when artist already in catalog`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist("artist-1"))

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", "album-1", listOf("artist-1"))),
            userId,
        )

        verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncArtistDetails }) }
    }

    @Test
    fun `syncForTracks does nothing when all tracks are in catalog`() {
        every { appTrackRepository.findByTrackIds(any()) } returns listOf(track("t1"), track("t2"))

        controller.syncForTracks(
            listOf(
                CatalogSyncRequest("t1", "album-1", listOf("artist-1")),
                CatalogSyncRequest("t2", "album-2", listOf("artist-2")),
            ),
            userId,
        )

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `syncForTracks handles track with no albumId - only syncs artist`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncForTracks(
            listOf(CatalogSyncRequest("t1", null, listOf("artist-1"))),
            userId,
        )

        verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncAlbumDetails }) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `syncForTracks deduplicates albums across multiple missing tracks`() {
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncForTracks(
            listOf(
                CatalogSyncRequest("t1", "album-shared", listOf("artist-1")),
                CatalogSyncRequest("t2", "album-shared", listOf("artist-1")),
            ),
            userId,
        )

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-shared")) }
    }

    // --- syncAlbums ---

    @Test
    fun `syncAlbums enqueues SyncAlbumDetails for new albums`() {
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncAlbums(listOf("album-1", "album-2"), emptyList(), userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-1")) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-2")) }
    }

    @Test
    fun `syncAlbums calls syncArtists for all artist ids`() {
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncAlbums(listOf("album-1"), listOf("artist-1"), userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `syncAlbums with empty albumIds still syncs artists`() {
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncAlbums(emptyList(), listOf("artist-1"), userId)

        verify(exactly = 0) { appAlbumRepository.findByAlbumIds(any()) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    }

    // --- syncArtists ---

    @Test
    fun `syncArtists enqueues SyncArtistDetails for missing artists`() {
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        controller.syncArtists(listOf("artist-1", "artist-2"), userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-2", userId)) }
    }

    @Test
    fun `syncArtists skips artists already in catalog`() {
        every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist("artist-1"))

        controller.syncArtists(listOf("artist-1"), userId)

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `syncArtists with empty list does nothing`() {
        controller.syncArtists(emptyList(), userId)

        verify(exactly = 0) { appArtistRepository.findByArtistIds(any()) }
        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }
}
