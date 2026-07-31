package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.AuthNotificationPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
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

class SpotifyAccessTokenAdapterTests {

  private val spotifyAuth: SpotifyAuthPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()
  private val tokenEncryption: TokenEncryptionPort = mockk()
  private val authNotification: AuthNotificationPort = mockk(relaxed = true)

  private val adapter = SpotifyAccessTokenAdapter(spotifyAuth, userRepository, tokenEncryption, authNotification)

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
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-access") } returns "plain-access".right()

    val result = adapter.getValidAccessToken()

    assertThat(result).isEqualTo(AccessToken("plain-access"))
    verify(exactly = 0) { spotifyAuth.refreshToken(any()) }
  }

  @Test
  fun `refreshes token when expiring within 5 minutes`() {
    val user = buildUser(Clock.System.now() + 3.minutes)
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
    every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns SpotifyRefreshedTokens(
      accessToken = AccessToken("new-access"),
      refreshToken = null,
      expiresInSeconds = 3600,
    ).right()
    every { tokenEncryption.encrypt("new-access") } returns "enc-new-access".right()
    every { userRepository.upsert(any()) } just runs

    val result = adapter.getValidAccessToken()

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
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
    every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns SpotifyRefreshedTokens(
      accessToken = AccessToken("new-access"),
      refreshToken = RefreshToken("new-refresh"),
      expiresInSeconds = 3600,
    ).right()
    every { tokenEncryption.encrypt("new-access") } returns "enc-new-access".right()
    every { tokenEncryption.encrypt("new-refresh") } returns "enc-new-refresh".right()
    every { userRepository.upsert(any()) } just runs

    adapter.getValidAccessToken()

    val upsertedSlot = slot<User>()
    verify { userRepository.upsert(capture(upsertedSlot)) }
    assertThat(upsertedSlot.captured.encryptedRefreshToken).isEqualTo("enc-new-refresh")
  }

  @Test
  fun `throws when user not found`() {
    every { userRepository.get() } returns null

    assertThatThrownBy { adapter.getValidAccessToken() }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("No user found")
  }

  @Test
  fun `throws when token refresh fails`() {
    val user = buildUser(Clock.System.now() + 1.minutes)
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
    every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns AuthError.TOKEN_REFRESH_FAILED.left()

    assertThatThrownBy { adapter.getValidAccessToken() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("user-1")
  }

  @Test
  fun `notifies once when token refresh fails`() {
    val user = buildUser(Clock.System.now() + 1.minutes)
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
    every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returns AuthError.TOKEN_REFRESH_FAILED.left()

    assertThatThrownBy { adapter.getValidAccessToken() }.isInstanceOf(IllegalStateException::class.java)
    assertThatThrownBy { adapter.getValidAccessToken() }.isInstanceOf(IllegalStateException::class.java)

    verify(exactly = 1) { authNotification.notifyTokenRefreshFailed() }
  }

  @Test
  fun `notifies again after a successful refresh following a previous failure`() {
    val user = buildUser(Clock.System.now() + 1.minutes)
    every { userRepository.get() } returns user
    every { tokenEncryption.decrypt("enc-refresh") } returns "plain-refresh".right()
    every { spotifyAuth.refreshToken(RefreshToken("plain-refresh")) } returnsMany listOf(
      AuthError.TOKEN_REFRESH_FAILED.left(),
      SpotifyRefreshedTokens(accessToken = AccessToken("new-access"), refreshToken = null, expiresInSeconds = 3600).right(),
      AuthError.TOKEN_REFRESH_FAILED.left(),
    )
    every { tokenEncryption.encrypt("new-access") } returns "enc-new-access".right()
    every { userRepository.upsert(any()) } just runs

    assertThatThrownBy { adapter.getValidAccessToken() }.isInstanceOf(IllegalStateException::class.java)
    adapter.getValidAccessToken()
    assertThatThrownBy { adapter.getValidAccessToken() }.isInstanceOf(IllegalStateException::class.java)

    verify(exactly = 2) { authNotification.notifyTokenRefreshFailed() }
  }
}
