package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class UserProfileUpdateAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyAuth: SpotifyAuthPort = mockk()
    private val outboxPort: OutboxPort = mockk()

    private val adapter = UserProfileUpdateAdapter(userRepository, spotifyAccessToken, spotifyAuth, outboxPort)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")

    private fun buildUser(id: String = "user-1", displayName: String = "Old Name") = User(
        spotifyUserId = UserId(id),
        displayName = displayName,
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Clock.System.now() + 1.hours,
        lastLoginAt = Clock.System.now(),
    )

    // --- enqueueUpdates tests ---

    @Test
    fun `enqueueUpdates does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        adapter.enqueueUpdates()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `enqueueUpdates enqueues one task per user`() {
        every { userRepository.findAll() } returns listOf(buildUser("user-1"), buildUser("user-2"))
        every { outboxPort.enqueue(any()) } just runs

        adapter.enqueueUpdates()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile(UserId("user-1"))) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile(UserId("user-2"))) }
    }

    // --- update tests ---

    @Test
    fun `update updates displayName when profile returns a changed name`() {
        val user = buildUser(displayName = "Old Name")
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns SpotifyProfile(
            id = SpotifyProfileId("user-1"),
            displayName = "New Name",
        ).right()
        every { userRepository.upsert(any()) } just runs

        adapter.update(userId)

        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.displayName).isEqualTo("New Name")
    }

    @Test
    fun `update does not upsert when displayName is unchanged`() {
        val user = buildUser(displayName = "Same Name")
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns SpotifyProfile(
            id = SpotifyProfileId("user-1"),
            displayName = "Same Name",
        ).right()

        adapter.update(userId)

        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `update skips when user not found`() {
        every { userRepository.findById(userId) } returns null

        adapter.update(userId)

        verify(exactly = 0) { spotifyAccessToken.getValidAccessToken(any()) }
        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `update logs error when profile fetch fails`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns AuthError.PROFILE_FETCH_FAILED.left()

        adapter.update(userId)

        verify(exactly = 0) { userRepository.upsert(any()) }
    }
}
