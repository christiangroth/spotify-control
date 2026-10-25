package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.port.out.user.ApiTokenRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ApiTokenRepositoryAdapterTests {

  @Inject
  lateinit var apiTokenRepository: ApiTokenRepositoryPort

  private fun token(): ApiToken {
    val id = UUID.randomUUID().toString()
    return ApiToken(
      id = ApiTokenId(id),
      name = "token-$id",
      tokenHash = "hash-$id",
      tokenPrefix = "spctl_abcd",
      scopes = setOf(ApiTokenScope.READ, ApiTokenScope.WRITE),
      active = true,
      createdAt = Instant.fromEpochSeconds(1_000),
      lastUsedAt = null,
    )
  }

  @Test
  fun `insert and findByHash round-trips a token`() {
    val token = token()
    apiTokenRepository.insert(token)

    assertThat(apiTokenRepository.findByHash(token.tokenHash)).isEqualTo(token)
    assertThat(apiTokenRepository.findAll()).contains(token)
  }

  @Test
  fun `setActive, setScopes and updateLastUsedAt modify an existing token`() {
    val token = token()
    apiTokenRepository.insert(token)

    assertThat(apiTokenRepository.setActive(token.id, false)).isTrue()
    assertThat(apiTokenRepository.setScopes(token.id, setOf(ApiTokenScope.SPOTIFY_READ))).isTrue()
    apiTokenRepository.updateLastUsedAt(token.id, Instant.fromEpochSeconds(2_000))

    assertThat(apiTokenRepository.findByHash(token.tokenHash)).isEqualTo(
      token.copy(active = false, scopes = setOf(ApiTokenScope.SPOTIFY_READ), lastUsedAt = Instant.fromEpochSeconds(2_000)),
    )
  }

  @Test
  fun `modifications and delete report unknown tokens`() {
    val unknown = ApiTokenId("unknown-${UUID.randomUUID()}")

    assertThat(apiTokenRepository.setActive(unknown, true)).isFalse()
    assertThat(apiTokenRepository.setScopes(unknown, setOf(ApiTokenScope.READ))).isFalse()
    assertThat(apiTokenRepository.delete(unknown)).isFalse()
  }

  @Test
  fun `delete removes a token`() {
    val token = token()
    apiTokenRepository.insert(token)

    assertThat(apiTokenRepository.delete(token.id)).isTrue()
    assertThat(apiTokenRepository.findByHash(token.tokenHash)).isNull()
  }
}
