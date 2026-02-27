package de.chrgroth.spotify.control.application.quarkus

import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileUpdateJob(
    private val userProfileUpdate: UserProfileUpdatePort,
) {

    @Scheduled(cron = "0 0 4 * * ?")
    fun run() {
        logger.info { "Running scheduled user profile update" }
        userProfileUpdate.updateUserProfiles()
    }

    companion object : KLogging()
}
