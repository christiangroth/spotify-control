package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
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

    override fun handleFetchRecentlyPlayedForUser(userId: UserId): Either<OutboxError, Unit> = try {
        recentlyPlayed.fetchAndPersistForUser(userId)
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handleFetchRecentlyPlayedForUser for user ${userId.value}" }
        OutboxError("Unexpected error in fetchAndPersistForUser: ${e.message}", e).left()
    }

    override fun handleUpdateUserProfileForUser(userId: UserId): Either<OutboxError, Unit> = try {
        userProfileUpdate.updateUserProfile(userId)
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handleUpdateUserProfileForUser for user ${userId.value}" }
        OutboxError("Unexpected error in updateUserProfile: ${e.message}", e).left()
    }

    companion object : KLogging()
}
