package de.chrgroth.spotify.control.domain

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
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
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)

    private val adapter = CatalogAdapter(
        spotifyAccessToken,
        spotifyCatalog,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        appPlaybackRepository,
        userRepository,
        outboxPort,
        dashboardRefresh,
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
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.upsertAll(listOf(spotifyArtist)) } just runs

        adapter.syncArtistDetails(artistId, userId)

        verify { appArtistRepository.upsertAll(listOf(spotifyArtist)) }
    }

    @Test
    fun `syncArtistDetails always fetches and updates artist even when already in catalog`() {
        val artistId = "artist-already-synced"
        val updatedArtist = AppArtist(
            artistId = artistId,
            artistName = "Known Artist",
            genre = "rock",
            lastSync = kotlin.time.Clock.System.now(),
        )
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns updatedArtist.right()
        every { appArtistRepository.upsertAll(listOf(updatedArtist)) } just runs

        adapter.syncArtistDetails(artistId, userId)

        verify { spotifyCatalog.getArtist(userId, accessToken, artistId) }
        verify { appArtistRepository.upsertAll(listOf(updatedArtist)) }
    }
}
