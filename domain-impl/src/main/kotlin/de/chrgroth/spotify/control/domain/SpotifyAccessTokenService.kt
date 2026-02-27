package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SpotifyAccessTokenService(
    private val spotifyAuth: SpotifyAuthPort,
    private val userRepository: UserRepositoryPort,
    private val tokenEncryption: TokenEncryptionPort,
) : SpotifyAccessTokenPort {

    override fun getValidAccessToken(userId: UserId): AccessToken {
        val user = requireNotNull(userRepository.findById(userId)) { "User not found: ${userId.value}" }
        return if (isTokenExpiringSoon(user)) {
            logger.info { "Token expiring soon, refreshing for user: ${userId.value}" }
            refreshAndPersist(user)
        } else {
            AccessToken(tokenEncryption.decrypt(user.encryptedAccessToken))
        }
    }

    private fun isTokenExpiringSoon(user: User): Boolean =
        user.tokenExpiresAt <= Clock.System.now() + TOKEN_REFRESH_BUFFER

    private fun refreshAndPersist(user: User): AccessToken {
        val refreshToken = RefreshToken(tokenEncryption.decrypt(user.encryptedRefreshToken))
        val refreshed = spotifyAuth.refreshToken(refreshToken)
        val now = Clock.System.now()
        userRepository.upsert(
            user.copy(
                encryptedAccessToken = tokenEncryption.encrypt(refreshed.accessToken.value),
                encryptedRefreshToken = refreshed.refreshToken
                    ?.let { tokenEncryption.encrypt(it.value) }
                    ?: user.encryptedRefreshToken,
                tokenExpiresAt = now + refreshed.expiresInSeconds.seconds,
            )
        )
        return refreshed.accessToken
    }

    companion object : KLogging() {
        private val TOKEN_REFRESH_BUFFER = 5.minutes
    }
}
