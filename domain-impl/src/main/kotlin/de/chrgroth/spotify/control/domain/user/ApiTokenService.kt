package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.ApiTokenError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.model.user.CreatedApiToken
import de.chrgroth.spotify.control.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.ApiTokenRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@ApplicationScoped
@Suppress("Unused")
class ApiTokenService(
  private val apiTokenRepository: ApiTokenRepositoryPort,
) : ApiTokenPort {

  private val secureRandom = SecureRandom()

  override fun listTokens(): List<ApiToken> = apiTokenRepository.findAll().sortedBy { it.createdAt }

  override fun createToken(name: String, scopes: Set<ApiTokenScope>): Either<DomainError, CreatedApiToken> {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return ApiTokenError.NAME_BLANK.left()
    if (scopes.isEmpty()) return ApiTokenError.NO_SCOPES.left()
    val plainToken = generatePlainToken()
    val token = ApiToken(
      id = ApiTokenId(UUID.randomUUID().toString()),
      name = trimmedName,
      tokenHash = hash(plainToken),
      tokenPrefix = plainToken.take(TOKEN_PREFIX_DISPLAY_LENGTH),
      scopes = scopes,
      active = true,
      createdAt = Clock.System.now(),
      lastUsedAt = null,
    )
    apiTokenRepository.insert(token)
    logger.info { "Created API token '${token.name}' (${token.id.value}) with scopes $scopes" }
    return CreatedApiToken(token, plainToken).right()
  }

  override fun setActive(id: ApiTokenId, active: Boolean): Either<DomainError, Unit> =
    if (apiTokenRepository.setActive(id, active)) {
      logger.info { "${if (active) "Activated" else "Deactivated"} API token ${id.value}" }
      Unit.right()
    } else {
      ApiTokenError.TOKEN_NOT_FOUND.left()
    }

  override fun setScopes(id: ApiTokenId, scopes: Set<ApiTokenScope>): Either<DomainError, Unit> {
    if (scopes.isEmpty()) return ApiTokenError.NO_SCOPES.left()
    return if (apiTokenRepository.setScopes(id, scopes)) {
      logger.info { "Updated scopes of API token ${id.value} to $scopes" }
      Unit.right()
    } else {
      ApiTokenError.TOKEN_NOT_FOUND.left()
    }
  }

  override fun deleteToken(id: ApiTokenId): Either<DomainError, Unit> =
    if (apiTokenRepository.delete(id)) {
      logger.info { "Deleted API token ${id.value}" }
      Unit.right()
    } else {
      ApiTokenError.TOKEN_NOT_FOUND.left()
    }

  override fun authenticate(plainToken: String): ApiToken? {
    if (!plainToken.startsWith(TOKEN_PREFIX)) return null
    val token = apiTokenRepository.findByHash(hash(plainToken)) ?: return null
    if (!token.active) return null
    val now = Clock.System.now()
    // Usage is recorded with minute granularity so a burst of CLI calls does not turn into one write per request.
    val lastUsedAt = token.lastUsedAt
    if (lastUsedAt == null || now - lastUsedAt > LAST_USED_UPDATE_INTERVAL) {
      apiTokenRepository.updateLastUsedAt(token.id, now)
    }
    return token
  }

  private fun generatePlainToken(): String {
    val bytes = ByteArray(TOKEN_RANDOM_BYTES)
    secureRandom.nextBytes(bytes)
    return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  companion object : KLogging() {
    const val TOKEN_PREFIX = "spctl_"
    private const val TOKEN_RANDOM_BYTES = 32
    private const val TOKEN_PREFIX_DISPLAY_LENGTH = 10
    private val LAST_USED_UPDATE_INTERVAL = 1.minutes

    internal fun hash(plainToken: String): String =
      MessageDigest.getInstance("SHA-256").digest(plainToken.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
}
