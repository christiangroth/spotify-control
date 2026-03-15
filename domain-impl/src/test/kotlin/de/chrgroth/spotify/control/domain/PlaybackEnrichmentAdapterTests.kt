package de.chrgroth.spotify.control.domain

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.TrackSyncResult
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UseBulkFetchStatePort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlaybackEnrichmentAdapterTests {

    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyCatalog: SpotifyCatalogPort = mockk()
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
    private val userRepository: UserRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk()
    private val syncPoolRepository: AppSyncPoolRepositoryPort = mockk(relaxed = true)
    private val useBulkFetchState: UseBulkFetchStatePort = mockk(relaxed = true)
    private val appSyncService: AppSyncService = mockk(relaxed = true)

    private val adapter = CatalogAdapter(
        spotifyAccessToken,
        spotifyCatalog,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        appPlaybackRepository,
        userRepository,
        outboxPort,
        syncPoolRepository,
        useBulkFetchState,
        appSyncService,
    )

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")

    @Test
    fun `syncArtistDetails stores artist from Spotify response`() {
        val artistId = "artist-1"
        val spotifyArtist = AppArtist(
            artistId = artistId,
            artistName = "Real Artist Name",
            genre = "pop",
            additionalGenres = null,
            imageLink = "https://example.com/image.jpg",
            type = "artist",
            lastSync = kotlin.time.Instant.fromEpochSeconds(1),
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns emptyList()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.upsertAll(listOf(spotifyArtist)) } just runs

        adapter.syncArtistDetails(artistId, userId)

        verify { appArtistRepository.upsertAll(listOf(spotifyArtist)) }
    }

    @Test
    fun `syncArtistDetails skips update when artist already in catalog`() {
        val artistId = "artist-already-synced"
        val syncedArtist = AppArtist(
            artistId = artistId,
            artistName = "Known Artist",
            lastSync = kotlin.time.Clock.System.now(),
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(syncedArtist)

        adapter.syncArtistDetails(artistId, userId)

        verify(exactly = 0) { spotifyCatalog.getArtist(any(), any(), any()) }
        verify(exactly = 0) { appArtistRepository.upsertAll(any()) }
    }

    @Test
    fun `syncTrackDetails stores track and album from Spotify response`() {
        val trackId = "track-1"
        val track = AppTrack(
            id = TrackId(trackId),
            title = "Track One",
            albumId = AlbumId("album-1"),
            albumName = "Album One",
            artistId = ArtistId("artist-1"),
            artistName = "Artist One",
            discNumber = 1,
            durationMs = 200000,
            trackNumber = 3,
            type = "track",
            lastSync = kotlin.time.Instant.fromEpochSeconds(1),
        )
        val album = AppAlbum(
            id = AlbumId("album-1"),
            title = "Album One",
            artistId = ArtistId("artist-1"),
            artistName = "Artist One",
            lastSync = kotlin.time.Instant.fromEpochSeconds(1),
        )
        val syncResult = TrackSyncResult(track = track, album = album)

        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns emptyList()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTrack(userId, accessToken, trackId) } returns syncResult.right()
        every { appTrackRepository.upsertAll(listOf(track)) } just runs
        every { appAlbumRepository.upsertAll(listOf(album)) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.syncTrackDetails(trackId, userId)

        verify { appTrackRepository.upsertAll(listOf(track)) }
        verify { appAlbumRepository.upsertAll(listOf(album)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `syncTrackDetails enqueues SyncArtistDetails for all track artists`() {
        val trackId = "track-multi-artist"
        val track = AppTrack(
            id = TrackId(trackId),
            title = "Collab Track",
            albumId = AlbumId("album-1"),
            artistId = ArtistId("artist-1"),
            additionalArtistIds = listOf(ArtistId("artist-2"), ArtistId("artist-3")),
            lastSync = kotlin.time.Instant.fromEpochSeconds(1),
        )
        val album = AppAlbum(
            id = AlbumId("album-1"),
            title = "Album One",
            lastSync = kotlin.time.Instant.fromEpochSeconds(1),
        )
        val syncResult = TrackSyncResult(track = track, album = album)

        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns emptyList()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTrack(userId, accessToken, trackId) } returns syncResult.right()
        every { appTrackRepository.upsertAll(listOf(track)) } just runs
        every { appAlbumRepository.upsertAll(listOf(album)) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.syncTrackDetails(trackId, userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-2", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-3", userId)) }
    }

    @Test
    fun `syncTrackDetails skips update when track already in catalog`() {
        val trackId = "track-already-synced"
        val syncedTrack = AppTrack(
            id = TrackId(trackId),
            title = "Known Track",
            artistId = ArtistId("artist-1"),
            lastSync = kotlin.time.Clock.System.now(),
        )
        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns listOf(syncedTrack)

        adapter.syncTrackDetails(trackId, userId)

        verify(exactly = 0) { spotifyCatalog.getTrack(any(), any(), any()) }
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
        verify(exactly = 0) { appAlbumRepository.upsertAll(any()) }
    }
}
