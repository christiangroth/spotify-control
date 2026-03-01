package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.util.outbox.OutboxError
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class OutboxHandlerAdapter(
    private val recentlyPlayed: RecentlyPlayedPort,
    private val userProfileUpdate: UserProfileUpdatePort,
) : OutboxHandlerPort {

    override fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): Either<OutboxError, Unit> = try {
        recentlyPlayed.update(UserId(event.userId))
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchRecentlyPlayed) for user ${event.userId}" }
        OutboxError("Unexpected error in update: ${e.message}", e).left()
    }

    override fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<OutboxError, Unit> = try {
        userProfileUpdate.update(UserId(event.userId))
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(UpdateUserProfile) for user ${event.userId}" }
        OutboxError("Unexpected error in update: ${e.message}", e).left()
    }

    companion object : KLogging()
}
