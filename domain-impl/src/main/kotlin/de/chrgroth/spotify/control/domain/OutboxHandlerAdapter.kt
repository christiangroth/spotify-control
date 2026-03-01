package de.chrgroth.spotify.control.domain

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class OutboxHandlerAdapter(
    private val recentlyPlayed: RecentlyPlayedPort,
    private val userProfileUpdate: UserProfileUpdatePort,
) : OutboxHandlerPort {

    override fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): OutboxTaskResult = try {
        when (val result = recentlyPlayed.update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to fetch recently played for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to fetch recently played: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchRecentlyPlayed) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.UpdateUserProfile): OutboxTaskResult = try {
        when (val result = userProfileUpdate.update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
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
