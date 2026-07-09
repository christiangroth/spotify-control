package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.user.LoginServicePort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class LoginService(
  private val spotifyAuth: SpotifyAuthPort,
  private val userRepository: UserRepositoryPort,
  private val tokenEncryption: TokenEncryptionPort,
) : LoginServicePort {

  override fun handleCallback(code: String): Either<DomainError, UserId> = try {
    either {
      val tokens = spotifyAuth.exchangeCode(code).bind()
      val profile = spotifyAuth.getUserProfile(tokens.accessToken).bind()
      val userId = UserId(profile.id.value)

      val existingUsers = userRepository.findAll()
      if (existingUsers.isNotEmpty() && existingUsers.none { it.spotifyUserId == userId }) {
        logger.warn { "Login denied for user: ${userId.value} - a different user is already registered" }
        raise(AuthError.ANOTHER_USER_ALREADY_REGISTERED)
      }

      val encryptedAccess = tokenEncryption.encrypt(tokens.accessToken.value).bind()
      val encryptedRefresh = tokenEncryption.encrypt(tokens.refreshToken.value).bind()
      val now = Clock.System.now()
      userRepository.upsert(
        User(
          spotifyUserId = userId,
          displayName = profile.displayName,
          encryptedAccessToken = encryptedAccess,
          encryptedRefreshToken = encryptedRefresh,
          tokenExpiresAt = now + tokens.expiresInSeconds.seconds,
          lastLoginAt = now,
        )
      )

      userId
    }
  } catch (e: Exception) {
    logger.error(e) { "Unexpected error during login callback" }
    AuthError.UNEXPECTED.left()
  }

  companion object : KLogging()
}
