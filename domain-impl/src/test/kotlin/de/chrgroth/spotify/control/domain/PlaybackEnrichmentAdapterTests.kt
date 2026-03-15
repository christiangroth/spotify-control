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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Instant
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
    fun `syncArtistDetails stores artist from Spotify response`() {
        val artistId = "artist-1"
        val spotifyArtist = AppArtist(
            artistId = artistId,
            artistName = "Real Artist Name",
            genre = "pop",
            additionalGenres = null,
            imageLink = "https://example.com/image.jpg",
            type = "artist",
            lastSync = Instant.fromEpochSeconds(1),
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns emptyList()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.upsertAll(listOf(spotifyArtist)) } just runs

        adapter.syncArtistDetails(artistId, userId)

        verify { appArtistRepository.upsertAll(listOf(spotifyArtist)) }
    }

    @Test
    fun `syncArtistDetails skips update when artist already recently synced`() {
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
    fun `syncArtistDetails re-syncs artist with DISTANT_PAST lastSync`() {
        val artistId = "artist-never-synced"
        val legacyArtist = AppArtist(
            artistId = artistId,
            artistName = "Legacy Artist",
            lastSync = Instant.DISTANT_PAST,
        )
        val spotifyArtist = AppArtist(
            artistId = artistId,
            artistName = "Legacy Artist",
            genre = "rock",
            lastSync = Instant.fromEpochSeconds(1),
        )
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(legacyArtist)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getArtist(userId, accessToken, artistId) } returns spotifyArtist.right()
        every { appArtistRepository.upsertAll(listOf(spotifyArtist)) } just runs

        adapter.syncArtistDetails(artistId, userId)

        verify { appArtistRepository.upsertAll(listOf(spotifyArtist)) }
    }
}
