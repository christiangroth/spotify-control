package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
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

    private val adapter = UserProfileUpdateAdapter(userRepository, spotifyAccessToken, spotifyAuth)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")

    private fun buildUser(displayName: String = "Old Name") = User(
        spotifyUserId = userId,
        displayName = displayName,
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Clock.System.now() + 1.hours,
        lastLoginAt = Clock.System.now(),
    )

    @Test
    fun `updates displayName when profile returns a changed name`() {
        val user = buildUser("Old Name")
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns SpotifyProfile(
            id = SpotifyProfileId("user-1"),
            displayName = "New Name",
        ).right()
        every { userRepository.upsert(any()) } just runs

        adapter.updateUserProfiles()

        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.displayName).isEqualTo("New Name")
    }

    @Test
    fun `does not upsert when displayName is unchanged`() {
        val user = buildUser("Same Name")
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns SpotifyProfile(
            id = SpotifyProfileId("user-1"),
            displayName = "Same Name",
        ).right()

        adapter.updateUserProfiles()

        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `skips user when profile fetch fails`() {
        val user = buildUser()
        every { userRepository.findAll() } returns listOf(user)
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns AuthError.PROFILE_FETCH_FAILED.left()

        adapter.updateUserProfiles()

        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `continues with remaining users when one throws an exception`() {
        val userA = buildUser()
        val userB = User(
            spotifyUserId = UserId("user-2"),
            displayName = "User B",
            encryptedAccessToken = "enc-access-b",
            encryptedRefreshToken = "enc-refresh-b",
            tokenExpiresAt = Clock.System.now() + 1.hours,
            lastLoginAt = Clock.System.now(),
        )
        every { userRepository.findAll() } returns listOf(userA, userB)
        every { spotifyAccessToken.getValidAccessToken(userId) } throws RuntimeException("token error")
        every { spotifyAccessToken.getValidAccessToken(UserId("user-2")) } returns accessToken
        every { spotifyAuth.getUserProfile(accessToken) } returns SpotifyProfile(
            id = SpotifyProfileId("user-2"),
            displayName = "User B Updated",
        ).right()
        every { userRepository.upsert(any()) } just runs

        adapter.updateUserProfiles()

        val upsertedSlot = slot<User>()
        verify(exactly = 1) { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.spotifyUserId).isEqualTo(UserId("user-2"))
    }

    @Test
    fun `does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        adapter.updateUserProfiles()

        verify(exactly = 0) { spotifyAccessToken.getValidAccessToken(any()) }
        verify(exactly = 0) { userRepository.upsert(any()) }
    }
}
