package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.SpotifyRecentlyPlayedTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FetchRecentlyPlayedServiceTests {

    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort = mockk()
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()

    private val service = FetchRecentlyPlayedService(spotifyAccessToken, spotifyRecentlyPlayed, recentlyPlayedRepository)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("token")
    private val now = Clock.System.now()

    private fun track(index: Int) = SpotifyRecentlyPlayedTrack(
        trackId = "track-$index",
        trackName = "Track $index",
        artistIds = listOf("artist-id-$index"),
        artistNames = listOf("Artist $index"),
        playedAt = now - index.hours,
    )

    @Test
    fun `persists new tracks and returns count`() {
        val tracks = listOf(track(1), track(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(accessToken) } returns tracks.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val result = service.fetchAndPersist(userId)

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()).isEqualTo(2)
        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(2)
        assertThat(savedSlot.captured.map { it.trackId }).containsExactlyInAnyOrder("track-1", "track-2")
        assertThat(savedSlot.captured.all { it.spotifyUserId == userId }).isTrue()
    }

    @Test
    fun `skips duplicate tracks and returns count of new items`() {
        val tracks = listOf(track(1), track(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(accessToken) } returns tracks.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(tracks[0].playedAt)
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val result = service.fetchAndPersist(userId)

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1)
        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-2")
    }

    @Test
    fun `returns zero and does not call saveAll when all tracks are duplicates`() {
        val tracks = listOf(track(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(accessToken) } returns tracks.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(tracks[0].playedAt)

        val result = service.fetchAndPersist(userId)

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()).isEqualTo(0)
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `returns zero and does not call saveAll when no tracks returned`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(accessToken) } returns emptyList<SpotifyRecentlyPlayedTrack>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val result = service.fetchAndPersist(userId)

        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()).isEqualTo(0)
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `propagates error when spotify fetch fails`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(accessToken) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()

        val result = service.fetchAndPersist(userId)

        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(PlaybackError.RECENTLY_PLAYED_FETCH_FAILED)
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }
}
