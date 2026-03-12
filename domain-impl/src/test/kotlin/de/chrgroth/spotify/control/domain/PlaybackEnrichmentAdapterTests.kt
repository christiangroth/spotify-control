package de.chrgroth.spotify.control.domain

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
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

    private val adapter = CatalogAdapter(
        spotifyAccessToken,
        spotifyCatalog,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        appPlaybackRepository,
        userRepository,
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
            genres = listOf("pop"),
            imageLink = "https://example.com/image.jpg",
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(AppArtist(artistId = artistId, artistName = ""))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.updateEnrichmentData(artistId, "Real Artist Name", listOf("pop"), "https://example.com/image.jpg") } just runs

        adapter.enrichArtistDetails(artistId, userId)

        verify { appArtistRepository.updateEnrichmentData(artistId, "Real Artist Name", listOf("pop"), "https://example.com/image.jpg") }
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
        verify(exactly = 0) { appArtistRepository.updateEnrichmentData(any(), any(), any(), any()) }
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
            genres = listOf("rock"),
            imageLink = "https://example.com/image.jpg",
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(artistWithBlankName)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", listOf("rock"), "https://example.com/image.jpg") } just runs

        adapter.enrichArtistDetails(artistId, userId)

        verify { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", listOf("rock"), "https://example.com/image.jpg") }
    }

    @Test
    fun `enrichTrackDetailsBulk updates albumId for all unenriched tracks`() {
        val trackId1 = "track-1"
        val trackId2 = "track-2"
        val albumId1 = "album-1"
        val albumId2 = "album-2"
        val track1 = AppTrack(trackId = trackId1, trackTitle = "Track One", artistId = "artist-1")
        val track2 = AppTrack(trackId = trackId2, trackTitle = "Track Two", artistId = "artist-1")
        every { appTrackRepository.findByTrackIds(setOf(trackId1, trackId2)) } returns listOf(track1, track2)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTracks(userId, accessToken, listOf(trackId1, trackId2)) } returns
            mapOf(trackId1 to albumId1, trackId2 to albumId2).right()
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { appTrackRepository.updateAlbumId(any(), any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.enrichTrackDetailsBulk(listOf(trackId1, trackId2), userId)

        verify { appTrackRepository.updateAlbumId(trackId1, albumId1) }
        verify { appTrackRepository.updateAlbumId(trackId2, albumId2) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId1, userId)) }
        verify { outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId2, userId)) }
    }

    @Test
    fun `enrichTrackDetailsBulk skips already-enriched tracks`() {
        val enrichedTrackId = "track-enriched"
        val unenrichedTrackId = "track-unenriched"
        val albumId = "album-1"
        val enrichedTrack = AppTrack(
            trackId = enrichedTrackId,
            trackTitle = "Enriched Track",
            artistId = "artist-1",
            lastEnrichmentDate = kotlin.time.Clock.System.now(),
        )
        val unenrichedTrack = AppTrack(trackId = unenrichedTrackId, trackTitle = "Unenriched Track", artistId = "artist-1")
        every { appTrackRepository.findByTrackIds(setOf(enrichedTrackId, unenrichedTrackId)) } returns
            listOf(enrichedTrack, unenrichedTrack)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTracks(userId, accessToken, listOf(unenrichedTrackId)) } returns
            mapOf(unenrichedTrackId to albumId).right()
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { appTrackRepository.updateAlbumId(any(), any()) } just runs
        every { outboxPort.enqueue(any()) } just runs

        adapter.enrichTrackDetailsBulk(listOf(enrichedTrackId, unenrichedTrackId), userId)

        verify(exactly = 0) { spotifyCatalog.getTracks(userId, accessToken, listOf(enrichedTrackId, unenrichedTrackId)) }
        verify { spotifyCatalog.getTracks(userId, accessToken, listOf(unenrichedTrackId)) }
        verify { appTrackRepository.updateAlbumId(unenrichedTrackId, albumId) }
        verify(exactly = 0) { appTrackRepository.updateAlbumId(enrichedTrackId, any()) }
    }

    @Test
    fun `enrichTrackDetailsBulk skips all when all tracks already enriched`() {
        val trackId = "track-enriched"
        val enrichedTrack = AppTrack(
            trackId = trackId,
            trackTitle = "Enriched Track",
            artistId = "artist-1",
            lastEnrichmentDate = kotlin.time.Clock.System.now(),
        )
        every { appTrackRepository.findByTrackIds(setOf(trackId)) } returns listOf(enrichedTrack)

        adapter.enrichTrackDetailsBulk(listOf(trackId), userId)

        verify(exactly = 0) { spotifyCatalog.getTracks(any(), any(), any()) }
        verify(exactly = 0) { appTrackRepository.updateAlbumId(any(), any()) }
    }

    @Test
    fun `enrichTrackDetailsBulk handles tracks with null albumId from Spotify`() {
        val trackId = "track-no-album"
        val track = AppTrack(trackId = trackId, trackTitle = "Track Without Album", artistId = "artist-1")
        every { appTrackRepository.findByTrackIds(setOf(trackId)) } returns listOf(track)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getTracks(userId, accessToken, listOf(trackId)) } returns
            mapOf(trackId to null).right()

        adapter.enrichTrackDetailsBulk(listOf(trackId), userId)

        verify(exactly = 0) { appTrackRepository.updateAlbumId(any(), any()) }
        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }
}

