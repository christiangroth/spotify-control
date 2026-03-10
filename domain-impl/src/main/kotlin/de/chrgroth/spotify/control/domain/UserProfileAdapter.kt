package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class UserProfileAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyAuth: SpotifyAuthPort,
    private val outboxPort: OutboxPort,
) : UserProfilePort {

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

    override fun handle(event: DomainOutboxEvent.UpdateUserProfile): OutboxTaskResult = try {
        when (val result = update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on UpdateUserProfile for user ${event.userId.value}, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to update user profile for ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to update user profile: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(UpdateUserProfile) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    companion object : KLogging()
}
