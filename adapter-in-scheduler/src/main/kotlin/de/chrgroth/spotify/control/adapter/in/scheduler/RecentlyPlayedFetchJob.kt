package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.FetchAllRecentlyPlayedPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedFetchJob(
    private val fetchAllRecentlyPlayed: FetchAllRecentlyPlayedPort,
) {

    @Scheduled(cron = "0 0/15 * * * ?")
    fun run() {
        logger.info { "Running scheduled recently played fetch" }
        fetchAllRecentlyPlayed.fetchAndPersistForAllUsers()
    }

    companion object : KLogging()
}
