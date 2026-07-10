package de.chrgroth.spotify.control.domain.user

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserProfileServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyAuth: SpotifyAuthPort = mockk()
  private val outboxPort: OutboxPort = mockk()

  private val service = UserProfileService(userRepository, spotifyAccessToken, spotifyAuth, outboxPort)

  private fun user(displayName: String) = User(
    spotifyUserId = UserId("user-1"),
    displayName = displayName,
    encryptedAccessToken = "encrypted-access",
    encryptedRefreshToken = "encrypted-refresh",
    tokenExpiresAt = kotlin.time.Clock.System.now(),
    lastLoginAt = kotlin.time.Clock.System.now(),
  )

  @Test
  fun `getDisplayName resolves once and caches for subsequent calls`() {
    every { userRepository.get() } returns user("Existing User")

    val first = service.getDisplayName()
    val second = service.getDisplayName()

    assertThat(first).isEqualTo("Existing User")
    assertThat(second).isEqualTo("Existing User")
    verify(exactly = 1) { userRepository.get() }
  }

  @Test
  fun `getDisplayName returns null and does not cache when no user is registered yet`() {
    every { userRepository.get() } returns null

    val first = service.getDisplayName()
    val second = service.getDisplayName()

    assertThat(first).isNull()
    assertThat(second).isNull()
    verify(exactly = 2) { userRepository.get() }
  }

  @Test
  fun `update refreshes the cached display name when it changed`() {
    every { userRepository.get() } returns user("Old Name")
    every { spotifyAccessToken.getValidAccessToken() } returns AccessToken("access")
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns SpotifyProfile(SpotifyProfileId("user-1"), "New Name").right()
    every { userRepository.upsert(any()) } just runs

    service.getDisplayName()
    val result = service.update()

    assertThat(result.isRight()).isTrue()
    assertThat(service.getDisplayName()).isEqualTo("New Name")
    verify(exactly = 1) { userRepository.upsert(any()) }
  }

  @Test
  fun `handle delegates to update`() {
    every { userRepository.get() } returns user("Same Name")
    every { spotifyAccessToken.getValidAccessToken() } returns AccessToken("access")
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns SpotifyProfile(SpotifyProfileId("user-1"), "Same Name").right()

    val result = service.handle(DomainOutboxEvent.UpdateUserProfile())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { userRepository.upsert(any()) }
  }
}
