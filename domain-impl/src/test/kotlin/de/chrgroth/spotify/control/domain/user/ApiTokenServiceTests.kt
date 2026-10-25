package de.chrgroth.spotify.control.domain.user

import de.chrgroth.spotify.control.domain.error.ApiTokenError
import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.port.out.user.ApiTokenRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class ApiTokenServiceTests {

  private val repository: ApiTokenRepositoryPort = mockk()
  private val service = ApiTokenService(repository)

  private fun token(plainToken: String, active: Boolean = true, lastUsedAgo: Duration? = null) = ApiToken(
    id = ApiTokenId("token-1"),
    name = "claude",
    tokenHash = ApiTokenService.hash(plainToken),
    tokenPrefix = plainToken.take(10),
    scopes = setOf(ApiTokenScope.READ),
    active = active,
    createdAt = Clock.System.now(),
    lastUsedAt = lastUsedAgo?.let { Clock.System.now() - it },
  )

  @Test
  fun `createToken stores only the hash and returns the plain token once`() {
    val stored = slot<ApiToken>()
    every { repository.insert(capture(stored)) } just runs

    val created = service.createToken("  claude  ", setOf(ApiTokenScope.READ, ApiTokenScope.WRITE)).getOrNull()!!

    assertThat(created.plainToken).startsWith(ApiTokenService.TOKEN_PREFIX)
    assertThat(stored.captured.name).isEqualTo("claude")
    assertThat(stored.captured.active).isTrue()
    assertThat(stored.captured.scopes).containsExactlyInAnyOrder(ApiTokenScope.READ, ApiTokenScope.WRITE)
    assertThat(stored.captured.tokenHash).isEqualTo(ApiTokenService.hash(created.plainToken)).isNotEqualTo(created.plainToken)
    assertThat(created.plainToken).startsWith(stored.captured.tokenPrefix)
  }

  @Test
  fun `createToken rejects blank name and empty scopes`() {
    assertThat(service.createToken(" ", setOf(ApiTokenScope.READ)).leftOrNull()).isEqualTo(ApiTokenError.NAME_BLANK)
    assertThat(service.createToken("claude", emptySet()).leftOrNull()).isEqualTo(ApiTokenError.NO_SCOPES)
    verify(exactly = 0) { repository.insert(any()) }
  }

  @Test
  fun `authenticate returns active token and records usage`() {
    val plain = "spctl_secret"
    every { repository.findByHash(ApiTokenService.hash(plain)) } returns token(plain)
    every { repository.updateLastUsedAt(any(), any()) } just runs

    assertThat(service.authenticate(plain)?.id).isEqualTo(ApiTokenId("token-1"))
    verify(exactly = 1) { repository.updateLastUsedAt(ApiTokenId("token-1"), any()) }
  }

  @Test
  fun `authenticate skips usage write when token was used recently`() {
    val plain = "spctl_secret"
    every { repository.findByHash(any()) } returns token(plain, lastUsedAgo = 10.seconds)

    assertThat(service.authenticate(plain)).isNotNull()
    verify(exactly = 0) { repository.updateLastUsedAt(any(), any()) }
  }

  @Test
  fun `authenticate rejects inactive token`() {
    val plain = "spctl_secret"
    every { repository.findByHash(any()) } returns token(plain, active = false, lastUsedAgo = 2.hours)

    assertThat(service.authenticate(plain)).isNull()
    verify(exactly = 0) { repository.updateLastUsedAt(any(), any()) }
  }

  @Test
  fun `authenticate rejects unknown tokens and skips lookup for malformed ones`() {
    every { repository.findByHash(any()) } returns null

    assertThat(service.authenticate("spctl_unknown")).isNull()
    assertThat(service.authenticate("not-a-token")).isNull()
    verify(exactly = 1) { repository.findByHash(any()) }
  }

  @Test
  fun `setActive, setScopes and deleteToken report unknown tokens`() {
    val id = ApiTokenId("missing")
    every { repository.setActive(id, any()) } returns false
    every { repository.setScopes(id, any()) } returns false
    every { repository.delete(id) } returns false

    assertThat(service.setActive(id, false).leftOrNull()).isEqualTo(ApiTokenError.TOKEN_NOT_FOUND)
    assertThat(service.setScopes(id, setOf(ApiTokenScope.READ)).leftOrNull()).isEqualTo(ApiTokenError.TOKEN_NOT_FOUND)
    assertThat(service.setScopes(id, emptySet()).leftOrNull()).isEqualTo(ApiTokenError.NO_SCOPES)
    assertThat(service.deleteToken(id).leftOrNull()).isEqualTo(ApiTokenError.TOKEN_NOT_FOUND)
  }

  @Test
  fun `setActive toggles an existing token`() {
    val id = ApiTokenId("token-1")
    every { repository.setActive(id, false) } returns true

    assertThat(service.setActive(id, false).isRight()).isTrue()
    verify(exactly = 1) { repository.setActive(id, false) }
  }
}
