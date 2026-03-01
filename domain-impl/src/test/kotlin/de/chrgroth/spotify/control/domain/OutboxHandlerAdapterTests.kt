package de.chrgroth.spotify.control.domain

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
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
    private val fetchEvent = DomainOutboxEvent.FetchRecentlyPlayed(userId.value)
    private val updateEvent = DomainOutboxEvent.UpdateUserProfile(userId.value)

    @Test
    fun `handle FetchRecentlyPlayed delegates to RecentlyPlayedPort successfully`() {
        every { recentlyPlayed.update(userId) } just runs

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { recentlyPlayed.update(userId) }
    }

    @Test
    fun `handle FetchRecentlyPlayed returns left on unexpected exception`() {
        every { recentlyPlayed.update(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `handle UpdateUserProfile delegates to UserProfileUpdatePort successfully`() {
        every { userProfileUpdate.update(userId) } just runs

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { userProfileUpdate.update(userId) }
    }

    @Test
    fun `handle UpdateUserProfile returns left on unexpected exception`() {
        every { userProfileUpdate.update(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }
}
