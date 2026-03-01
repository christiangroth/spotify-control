package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedFetchJob(
    private val recentlyPlayed: RecentlyPlayedPort,
) {

    @Scheduled(cron = "0 0/15 * * * ?")
    fun run() {
        logger.info { "Running scheduled recently played fetch" }
        recentlyPlayed.enqueueUpdates()
    }

    companion object : KLogging()
}
