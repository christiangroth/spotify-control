package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.FetchRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.junit.jupiter.api.Test

class FetchAllRecentlyPlayedAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val fetchRecentlyPlayed: FetchRecentlyPlayedPort = mockk()

    private val adapter = FetchAllRecentlyPlayedAdapter(userRepository, fetchRecentlyPlayed)

    private fun buildUser(id: String) = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Clock.System.now() + 1.hours,
        lastLoginAt = Clock.System.now(),
    )

    @Test
    fun `does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 0) { fetchRecentlyPlayed.fetchAndPersist(any()) }
    }

    @Test
    fun `fetches recently played for all users`() {
        val userA = buildUser("user-1")
        val userB = buildUser("user-2")
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) } returns 3.right()
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) } returns 0.right()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) }
        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) }
    }

    @Test
    fun `continues with remaining users when one returns an error`() {
        val userA = buildUser("user-1")
        val userB = buildUser("user-2")
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) } returns 1.right()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) }
        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) }
    }

    @Test
    fun `continues with remaining users when one throws an exception`() {
        val userA = buildUser("user-1")
        val userB = buildUser("user-2")
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) } throws RuntimeException("unexpected")
        every { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) } returns 2.right()

        adapter.fetchAndPersistForAllUsers()

        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-1")) }
        verify(exactly = 1) { fetchRecentlyPlayed.fetchAndPersist(UserId("user-2")) }
    }
}
