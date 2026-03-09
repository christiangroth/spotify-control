package de.chrgroth.spotify.control.domain

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAlbumDetailsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyArtistDetailsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyTrackDetailsPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlaybackEnrichmentAdapterTests {

    private val userRepository: UserRepositoryPort = mockk(relaxed = true)
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyCurrentlyPlaying: SpotifyCurrentlyPlayingPort = mockk(relaxed = true)
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort = mockk(relaxed = true)
    private val spotifyArtistDetails: SpotifyArtistDetailsPort = mockk()
    private val spotifyTrackDetails: SpotifyTrackDetailsPort = mockk()
    private val spotifyAlbumDetails: SpotifyAlbumDetailsPort = mockk()
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
    private val playbackState: PlaybackStatePort = mockk(relaxed = true)
    private val appEnrichmentService: AppEnrichmentService = mockk(relaxed = true)

    private val adapter = PlaybackAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyCurrentlyPlaying,
        spotifyRecentlyPlayed,
        spotifyArtistDetails,
        spotifyTrackDetails,
        spotifyAlbumDetails,
        currentlyPlayingRepository,
        recentlyPlayedRepository,
        recentlyPartialPlayedRepository,
        appPlaybackRepository,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        outboxPort,
        dashboardRefresh,
        playbackState,
        appEnrichmentService,
        minimumProgressSeconds = 0L,
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
        every { spotifyArtistDetails.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
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

        verify(exactly = 0) { spotifyArtistDetails.getArtist(any(), any(), any()) }
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
        every { spotifyArtistDetails.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", listOf("rock"), "https://example.com/image.jpg") } just runs

        adapter.enrichArtistDetails(artistId, userId)

        verify { appArtistRepository.updateEnrichmentData(artistId, "Recovered Name", listOf("rock"), "https://example.com/image.jpg") }
    }
}
