package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.LoginServicePort
import de.chrgroth.spotify.control.domain.port.`in`.UserServicePort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class LoginServiceAdapter(
    private val spotifyAuth: SpotifyAuthPort,
    private val userService: UserServicePort,
    private val userRepository: UserRepositoryPort,
    private val tokenEncryption: TokenEncryptionPort,
) : LoginServicePort {

    override fun handleCallback(code: String): Either<DomainError, UserId> = either {
        val tokens = spotifyAuth.exchangeCode(code).bind()
        val profile = spotifyAuth.getUserProfile(tokens.accessToken).bind()
        val userId = UserId(profile.id.value)

        if (!userService.isAllowed(userId)) {
            logger.warn { "Login denied for user: ${userId.value}" }
            raise(AuthError.USER_NOT_ALLOWED)
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

    companion object : KLogging()
}
