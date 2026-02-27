package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.DomainError
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
            refreshAndPersist(user).fold(
                ifLeft = { error("Failed to refresh access token for user ${userId.value}: ${it.code}") },
                ifRight = { it }
            )
        } else {
            tokenEncryption.decrypt(user.encryptedAccessToken).fold(
                ifLeft = { error("Failed to decrypt access token for user ${userId.value}: ${it.code}") },
                ifRight = { AccessToken(it) }
            )
        }
    }

    private fun isTokenExpiringSoon(user: User): Boolean =
        user.tokenExpiresAt <= Clock.System.now() + TOKEN_REFRESH_BUFFER

    private fun refreshAndPersist(user: User): Either<DomainError, AccessToken> = either {
        val plainRefresh = tokenEncryption.decrypt(user.encryptedRefreshToken).bind()
        val refreshToken = RefreshToken(plainRefresh)
        val refreshed = spotifyAuth.refreshToken(refreshToken).bind()
        val now = Clock.System.now()
        val encryptedAccess = tokenEncryption.encrypt(refreshed.accessToken.value).bind()
        val encryptedRefresh = refreshed.refreshToken
            ?.let { tokenEncryption.encrypt(it.value).bind() }
            ?: user.encryptedRefreshToken
        val updatedDisplayName = spotifyAuth.getUserProfile(refreshed.accessToken).fold(
            ifLeft = {
                logger.warn { "Failed to fetch profile during token refresh for user: ${user.spotifyUserId.value}, keeping existing displayName" }
                user.displayName
            },
            ifRight = { it.displayName },
        )
        userRepository.upsert(
            user.copy(
                displayName = updatedDisplayName,
                encryptedAccessToken = encryptedAccess,
                encryptedRefreshToken = encryptedRefresh,
                tokenExpiresAt = now + refreshed.expiresInSeconds.seconds,
            )
        )
        refreshed.accessToken
    }

    companion object : KLogging() {
        private val TOKEN_REFRESH_BUFFER = 5.minutes
    }
}
