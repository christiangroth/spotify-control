package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
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

class RecentlyPlayedAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort = mockk()
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()

    private val adapter = RecentlyPlayedAdapter(userRepository, spotifyAccessToken, spotifyRecentlyPlayed, recentlyPlayedRepository)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("token")
    private val now = Clock.System.now()

    private fun buildUser(id: String) = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
    )

    private fun item(index: Int, forUserId: UserId = userId) = RecentlyPlayedItem(
        spotifyUserId = forUserId,
        trackId = "track-$index",
        trackName = "Track $index",
        artistIds = listOf("artist-id-$index"),
        artistNames = listOf("Artist $index"),
        playedAt = now - index.hours,
    )

    @Test
    fun `does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 0) { spotifyAccessToken.getValidAccessToken(any()) }
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `persists new tracks for a single user`() {
        val user = buildUser("user-1")
        val items = listOf(item(1), item(2))
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        adapter.fetchAndPersistForAllUsers()

        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(2)
        assertThat(savedSlot.captured.map { it.trackId }).containsExactlyInAnyOrder("track-1", "track-2")
    }

    @Test
    fun `skips duplicate tracks`() {
        val user = buildUser("user-1")
        val items = listOf(item(1), item(2))
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        adapter.fetchAndPersistForAllUsers()

        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-2")
    }

    @Test
    fun `does not call saveAll when all tracks are duplicates`() {
        val user = buildUser("user-1")
        val items = listOf(item(1))
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `does not call saveAll when no tracks returned`() {
        val user = buildUser("user-1")
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `continues with remaining users when one returns a domain error`() {
        val userId2 = UserId("user-2")
        val userA = buildUser("user-1")
        val userB = buildUser("user-2")
        val items = listOf(item(1, userId2))
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        every { spotifyAccessToken.getValidAccessToken(userId2) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId2, accessToken) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId2, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 1) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `continues with remaining users when one throws an exception`() {
        val userId2 = UserId("user-2")
        val userA = buildUser("user-1")
        val userB = buildUser("user-2")
        val items = listOf(item(1, userId2))
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { spotifyAccessToken.getValidAccessToken(userId) } throws RuntimeException("token error")
        every { spotifyAccessToken.getValidAccessToken(userId2) } returns accessToken
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId2, accessToken) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId2, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 1) { recentlyPlayedRepository.saveAll(any()) }
    }
}
