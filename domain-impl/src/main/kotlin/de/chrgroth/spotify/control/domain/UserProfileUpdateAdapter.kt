package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileUpdateAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyAuth: SpotifyAuthPort,
    private val outboxPort: OutboxPort,
) : UserProfileUpdatePort {

    override fun updateUserProfiles() {
        val users = userRepository.findAll()
        logger.info { "Scheduling profile update for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(AppOutboxEvent.UpdateUserProfileForUser(user.spotifyUserId.value))
        }
    }

    override fun updateUserProfile(userId: UserId) {
        val user = userRepository.findById(userId) ?: run {
            logger.warn { "User not found for profile update: ${userId.value}" }
            return
        }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        spotifyAuth.getUserProfile(accessToken).fold(
            ifLeft = { logger.error { "Failed to fetch profile for user ${userId.value}: ${it.code}" } },
            ifRight = { profile ->
                if (profile.displayName != user.displayName) {
                    logger.info { "Updating displayName for user ${userId.value}" }
                    userRepository.upsert(user.copy(displayName = profile.displayName))
                }
            },
        )
    }

    companion object : KLogging()
}
