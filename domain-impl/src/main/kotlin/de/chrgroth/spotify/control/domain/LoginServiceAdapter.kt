package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.LoginResult
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

    override fun handleCallback(code: String): LoginResult {
        val tokens = spotifyAuth.exchangeCode(code)
        val profile = spotifyAuth.getUserProfile(tokens.accessToken)
        val userId = UserId(profile.id.value)

        if (!userService.isAllowed(userId)) {
            logger.warn { "Login denied for user: ${userId.value}" }
            return LoginResult.Failure("not_allowed")
        }

        val now = Clock.System.now()
        userRepository.upsert(
            User(
                spotifyUserId = userId,
                displayName = profile.displayName,
                encryptedAccessToken = tokenEncryption.encrypt(tokens.accessToken.value),
                encryptedRefreshToken = tokenEncryption.encrypt(tokens.refreshToken.value),
                tokenExpiresAt = now + tokens.expiresInSeconds.seconds,
                lastLoginAt = now,
            )
        )

        logger.info { "User logged in successfully: ${userId.value}" }
        return LoginResult.Success(userId)
    }

    companion object : KLogging()
}
