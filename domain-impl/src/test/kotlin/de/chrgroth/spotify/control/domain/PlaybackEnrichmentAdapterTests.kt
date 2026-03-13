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
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
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
    private val playlistRepository: PlaylistRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk()

    private val adapter = CatalogAdapter(
        spotifyAccessToken,
        spotifyCatalog,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        appPlaybackRepository,
        userRepository,
        playlistRepository,
        outboxPort,
    )

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")

    @Test
    fun `enrichArtistDetails updates artistName from Spotify response`() {
        val artistId = "artist-1"
        val spotifyArtist = AppArtist(
            artistId = artistId,
            artistName = "Real Artist Name",
            genre = "pop",
            additionalGenres = null,
            imageLink = "https://example.com/image.jpg",
            type = "artist",
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(AppArtist(artistId = artistId, artistName = ""))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.updateEnrichmentData(artistId, "Real Artist Name", "pop", null, "https://example.com/image.jpg", "artist") } just runs

        adapter.enrichArtistDetails(artistId, userId)

        verify { appArtistRepository.updateEnrichmentData(artistId, "Real Artist Name", "pop", null, "https://example.com/image.jpg", "artist") }
    }

    @Test
    fun `enrichArtistDetails skips update when artist already enriched`() {
        val artistId = "artist-already-enriched"
        val enrichedArtist = AppArtist(
            artistId = artistId,
            artistName = "Known Artist",
            lastEnrichmentDate = kotlin.time.Clock.System.now(),
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(enrichedArtist)

        adapter.enrichArtistDetails(artistId, userId)

        verify(exactly = 0) { spotifyCatalog.getArtist(any(), any(), any()) }
        verify(exactly = 0) { appArtistRepository.updateEnrichmentData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `enrichArtistDetails re-enriches when artist has enrichmentDate but blank name`() {
        val artistId = "artist-blank-name"
        val artistWithBlankName = AppArtist(
            artistId = artistId,
            artistName = "",
            imageLink = "https://example.com/image.jpg",
            lastEnrichmentDate = kotlin.time.Clock.System.now(),
        )
        val spotifyArtist = AppArtist(
            artistId = artistId,
            artistName = "Recovered Name",
            genre = "rock",
            additionalGenres = listOf("indie"),
            imageLink = "https://example.com/image.jpg",
            type = "artist",
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(artistWithBlankName)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", "rock", listOf("indie"), "https://example.com/image.jpg", "artist") } just runs

        adapter.enrichArtistDetails(artistId, userId)

        verify { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", "rock", listOf("indie"), "https://example.com/image.jpg", "artist") }
    }

    @Test
    fun `enrichTrackDetails updates track and album from Spotify response`() {
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

        val existingTrack = AppTrack(id = TrackId(trackId), title = "Track One", artistId = ArtistId("artist-1"))
        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns listOf(existingTrack)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTrack(userId, accessToken, trackId) } returns syncResult.right()
        every { appTrackRepository.updateTrackEnrichmentData(track) } just runs
        every { appAlbumRepository.upsertAll(listOf(album)) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.enrichTrackDetails(trackId, userId)

        verify { appTrackRepository.updateTrackEnrichmentData(track) }
        verify { appAlbumRepository.upsertAll(listOf(album)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-1", userId)) }
    }

    @Test
    fun `enrichTrackDetails enqueues EnrichArtistDetails for all track artists`() {
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

        val existingTrack2 = AppTrack(id = TrackId(trackId), title = "Collab Track", artistId = ArtistId("artist-1"))
        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns listOf(existingTrack2)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTrack(userId, accessToken, trackId) } returns syncResult.right()
        every { appTrackRepository.updateTrackEnrichmentData(track) } just runs
        every { appAlbumRepository.upsertAll(listOf(album)) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.enrichTrackDetails(trackId, userId)

        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-1", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-2", userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails("artist-3", userId)) }
    }

    @Test
    fun `enrichTrackDetails skips update when track already enriched`() {
        val trackId = "track-already-enriched"
        val enrichedTrack = AppTrack(
            id = TrackId(trackId),
            title = "Known Track",
            artistId = ArtistId("artist-1"),
            lastEnrichmentDate = kotlin.time.Clock.System.now(),
        )
        every { appTrackRepository.findByTrackIds(setOf(TrackId(trackId))) } returns listOf(enrichedTrack)

        adapter.enrichTrackDetails(trackId, userId)

        verify(exactly = 0) { spotifyCatalog.getTrack(any(), any(), any()) }
        verify(exactly = 0) { appTrackRepository.updateTrackEnrichmentData(any()) }
        verify(exactly = 0) { appAlbumRepository.upsertAll(any()) }
    }
}
