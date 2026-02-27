package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SpotifyAccessTokenServiceTests {

    private val spotifyAuth: SpotifyAuthPort = mockk()
    private val userRepository: UserRepositoryPort = mockk()
    private val tokenEncryption: TokenEncryptionPort = mockk()

    private val service = SpotifyAccessTokenService(spotifyAuth, userRepository, tokenEncryption)

    private val userId = UserId("user-1")

    private fun buildUser(expiresAt: kotlin.time.Instant) = User(
        spotifyUserId = userId,
        displayName = "Test User",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = expiresAt,
        lastLoginAt = Clock.System.now(),
    )

    @Test
    fun `returns existing access token when not expiring soon`() {
        val user = buildUser(Clock.System.now() + 1.hours)
        every { userRepository.findById(userId) } returns user
        every { tokenEncryption.decrypt("enc-access") } returns "plain-access".right()

        val result = service.getValidAccessToken(userId)

        assertThat(result).isEqualTo(AccessToken("plain-access"))
        verify(exactly = 0) { spotifyAuth.refreshToken(any()) }
    }

    @Test
    fun `refreshes token when expiring within 5 minutes`() {
        val user = buildUser(Clock.System.now() + 3.minutes)
        every { userRepository.findById(userId) } returns user
        every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
        every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns SpotifyRefreshedTokens(
            accessToken = AccessToken("new-access"),
            refreshToken = null,
            expiresInSeconds = 3600,
        ).right()
        every { tokenEncryption.encrypt("new-access") } returns "enc-new-access".right()
        every { userRepository.upsert(any()) } just runs

        val result = service.getValidAccessToken(userId)

        assertThat(result).isEqualTo(AccessToken("new-access"))
        verify { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) }
        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.encryptedAccessToken).isEqualTo("enc-new-access")
        assertThat(upsertedSlot.captured.encryptedRefreshToken).isEqualTo("enc-refresh")
    }

    @Test
    fun `persists rotated refresh token when spotify returns a new one`() {
        val user = buildUser(Clock.System.now() + 1.minutes)
        every { userRepository.findById(userId) } returns user
        every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
        every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns SpotifyRefreshedTokens(
            accessToken = AccessToken("new-access"),
            refreshToken = RefreshToken("new-refresh"),
            expiresInSeconds = 3600,
        ).right()
        every { tokenEncryption.encrypt("new-access") } returns "enc-new-access".right()
        every { tokenEncryption.encrypt("new-refresh") } returns "enc-new-refresh".right()
        every { userRepository.upsert(any()) } just runs

        service.getValidAccessToken(userId)

        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.encryptedRefreshToken).isEqualTo("enc-new-refresh")
    }

    @Test
    fun `throws when user not found`() {
        every { userRepository.findById(userId) } returns null

        assertThatThrownBy { service.getValidAccessToken(userId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("user-1")
    }

    @Test
    fun `throws when token refresh fails`() {
        val user = buildUser(Clock.System.now() + 1.minutes)
        every { userRepository.findById(userId) } returns user
        every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
        every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns AuthError.TOKEN_REFRESH_FAILED.left()

        assertThatThrownBy { service.getValidAccessToken(userId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("user-1")
    }
}
