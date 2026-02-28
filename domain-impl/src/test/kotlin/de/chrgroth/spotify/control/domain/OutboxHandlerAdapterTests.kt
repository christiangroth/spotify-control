package de.chrgroth.spotify.control.domain

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboxHandlerAdapterTests {

    private val recentlyPlayed: RecentlyPlayedPort = mockk()
    private val userProfileUpdate: UserProfileUpdatePort = mockk()

    private val adapter = OutboxHandlerAdapter(recentlyPlayed, userProfileUpdate)

    private val userId = UserId("user-1")

    @Test
    fun `handleFetchRecentlyPlayedForUser delegates to RecentlyPlayedPort successfully`() {
        every { recentlyPlayed.fetchAndPersistForUser(userId) } just runs

        val result = adapter.handleFetchRecentlyPlayedForUser(userId)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { recentlyPlayed.fetchAndPersistForUser(userId) }
    }

    @Test
    fun `handleFetchRecentlyPlayedForUser returns left on unexpected exception`() {
        every { recentlyPlayed.fetchAndPersistForUser(userId) } throws RuntimeException("connection error")

        val result = adapter.handleFetchRecentlyPlayedForUser(userId)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `handleUpdateUserProfileForUser delegates to UserProfileUpdatePort successfully`() {
        every { userProfileUpdate.updateUserProfile(userId) } just runs

        val result = adapter.handleUpdateUserProfileForUser(userId)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { userProfileUpdate.updateUserProfile(userId) }
    }

    @Test
    fun `handleUpdateUserProfileForUser returns left on unexpected exception`() {
        every { userProfileUpdate.updateUserProfile(userId) } throws RuntimeException("connection error")

        val result = adapter.handleUpdateUserProfileForUser(userId)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }
}
