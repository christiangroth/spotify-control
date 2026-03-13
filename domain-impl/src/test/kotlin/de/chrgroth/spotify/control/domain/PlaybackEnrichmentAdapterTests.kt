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
    )

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")

    @Test
    fun `syncMissingTracks updates tracks and albums from Spotify bulk response`() {
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
        )
        val album = AppAlbum(
            id = AlbumId("album-1"),
            title = "Album One",
            artistId = ArtistId("artist-1"),
            artistName = "Artist One",
        )
        val syncResult = TrackSyncResult(track = track, album = album)

        every { userRepository.findAll() } returns listOf(mockk { every { spotifyUserId } returns userId })
        every { syncPoolRepository.peekTracks(any()) } returns listOf(trackId)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTracks(userId, accessToken, listOf(trackId)) } returns listOf(syncResult).right()
        every { appTrackRepository.upsertAll(listOf(track)) } just runs
        every { appAlbumRepository.upsertAll(listOf(album)) } just runs

        adapter.handle(DomainOutboxEvent.SyncMissingTracks())

        verify { appTrackRepository.upsertAll(listOf(track)) }
        verify { appAlbumRepository.upsertAll(listOf(album)) }
        verify { syncPoolRepository.removeTracks(listOf(trackId)) }
        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
    }

    @Test
    fun `syncMissingTracks adds all track artists to artist pool`() {
        val trackId = "track-multi-artist"
        val track = AppTrack(
            id = TrackId(trackId),
            title = "Collab Track",
            albumId = AlbumId("album-1"),
            artistId = ArtistId("artist-1"),
            additionalArtistIds = listOf(ArtistId("artist-2"), ArtistId("artist-3")),
        )
        val album = AppAlbum(id = AlbumId("album-1"), title = "Album One")
        val syncResult = TrackSyncResult(track = track, album = album)

        every { userRepository.findAll() } returns listOf(mockk { every { spotifyUserId } returns userId })
        every { syncPoolRepository.peekTracks(any()) } returns listOf(trackId)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTracks(userId, accessToken, listOf(trackId)) } returns listOf(syncResult).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs

        adapter.handle(DomainOutboxEvent.SyncMissingTracks())

        verify { syncPoolRepository.addArtists(listOf("artist-1", "artist-2", "artist-3")) }
    }

    @Test
    fun `syncMissingTracks does nothing when pool is empty`() {
        every { userRepository.findAll() } returns listOf(mockk { every { spotifyUserId } returns userId })
        every { syncPoolRepository.peekTracks(any()) } returns emptyList()

        adapter.handle(DomainOutboxEvent.SyncMissingTracks())

        verify(exactly = 0) { spotifyCatalog.getTracks(any(), any(), any()) }
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
    }

    @Test
    fun `syncMissingArtists updates artists from Spotify bulk response`() {
        val artistId = "artist-1"
        val artist = AppArtist(
            artistId = artistId,
            artistName = "Real Artist Name",
            genre = "pop",
            imageLink = "https://example.com/image.jpg",
        )

        every { userRepository.findAll() } returns listOf(mockk { every { spotifyUserId } returns userId })
        every { syncPoolRepository.peekArtists(any()) } returns listOf(artistId)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtists(userId, accessToken, listOf(artistId)) } returns listOf(artist).right()
        every { appArtistRepository.upsertAll(listOf(artist)) } just runs

        adapter.handle(DomainOutboxEvent.SyncMissingArtists())

        verify { appArtistRepository.upsertAll(listOf(artist)) }
        verify { syncPoolRepository.removeArtists(listOf(artistId)) }
    }

    @Test
    fun `syncMissingArtists does nothing when pool is empty`() {
        every { userRepository.findAll() } returns listOf(mockk { every { spotifyUserId } returns userId })
        every { syncPoolRepository.peekArtists(any()) } returns emptyList()

        adapter.handle(DomainOutboxEvent.SyncMissingArtists())

        verify(exactly = 0) { spotifyCatalog.getArtists(any(), any(), any()) }
        verify(exactly = 0) { appArtistRepository.upsertAll(any()) }
    }
}
