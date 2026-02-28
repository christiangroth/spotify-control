package de.chrgroth.spotify.control.domain

import arrow.core.Either
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

    @Test
    fun `handleFetchRecentlyPlayed delegates to RecentlyPlayedPort successfully`() {
        every { recentlyPlayed.fetchAndPersistForAllUsers() } just runs

        val result = adapter.handleFetchRecentlyPlayed()

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { recentlyPlayed.fetchAndPersistForAllUsers() }
    }

    @Test
    fun `handleFetchRecentlyPlayed returns left on unexpected exception`() {
        every { recentlyPlayed.fetchAndPersistForAllUsers() } throws RuntimeException("connection error")

        val result = adapter.handleFetchRecentlyPlayed()

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `handleUpdateUserProfiles delegates to UserProfileUpdatePort successfully`() {
        every { userProfileUpdate.updateUserProfiles() } just runs

        val result = adapter.handleUpdateUserProfiles()

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { userProfileUpdate.updateUserProfiles() }
    }

    @Test
    fun `handleUpdateUserProfiles returns left on unexpected exception`() {
        every { userProfileUpdate.updateUserProfiles() } throws RuntimeException("connection error")

        val result = adapter.handleUpdateUserProfiles()

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }
}
