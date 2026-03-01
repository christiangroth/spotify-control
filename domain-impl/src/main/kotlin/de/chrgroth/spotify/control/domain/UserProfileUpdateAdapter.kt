package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
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

    override fun enqueueUpdates() {
        val users = userRepository.findAll()
        logger.info { "Scheduling profile update for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile(user.spotifyUserId))
        }
    }

    override fun update(userId: UserId): Either<DomainError, Unit> {
        val user = userRepository.findById(userId) ?: run {
            logger.warn { "User not found for profile update: ${userId.value}" }
            return Unit.right()
        }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyAuth.getUserProfile(accessToken).map { profile ->
            if (profile.displayName != user.displayName) {
                logger.info { "Updating displayName for user ${userId.value}" }
                userRepository.upsert(user.copy(displayName = profile.displayName))
            }
        }
    }

    companion object : KLogging()
}
