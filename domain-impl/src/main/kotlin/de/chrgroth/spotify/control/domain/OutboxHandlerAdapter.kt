package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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

    override fun handleFetchRecentlyPlayed(): Either<OutboxError, Unit> = try {
        recentlyPlayed.fetchAndPersistForAllUsers()
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handleFetchRecentlyPlayed" }
        OutboxError("Unexpected error in fetchAndPersistForAllUsers: ${e.message}", e).left()
    }

    override fun handleUpdateUserProfiles(): Either<OutboxError, Unit> = try {
        userProfileUpdate.updateUserProfiles()
        Unit.right()
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handleUpdateUserProfiles" }
        OutboxError("Unexpected error in updateUserProfiles: ${e.message}", e).left()
    }

    companion object : KLogging()
}
