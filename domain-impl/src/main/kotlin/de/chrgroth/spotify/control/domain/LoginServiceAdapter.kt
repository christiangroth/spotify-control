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

    override fun handleCallback(code: String): LoginResult =
        spotifyAuth.exchangeCode(code).flatMap { tokens ->
            spotifyAuth.getUserProfile(tokens.accessToken).flatMap { profile ->
                val userId = UserId(profile.id.value)
                if (!userService.isAllowed(userId)) {
                    logger.warn { "Login denied for user: ${userId.value}" }
                    DomainResult.Failure(AuthError.USER_NOT_ALLOWED)
                } else {
                    tokenEncryption.encrypt(tokens.accessToken.value).flatMap { encAccess ->
                        tokenEncryption.encrypt(tokens.refreshToken.value).map { encRefresh ->
                            val now = Clock.System.now()
                            userRepository.upsert(
                                User(
                                    spotifyUserId = userId,
                                    displayName = profile.displayName,
                                    encryptedAccessToken = encAccess,
                                    encryptedRefreshToken = encRefresh,
                                    tokenExpiresAt = now + tokens.expiresInSeconds.seconds,
                                    lastLoginAt = now,
                                )
                            )
                            userId
                        }
                    }
                }
            }
        }

    companion object : KLogging()
}
