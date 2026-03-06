package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.spotify.control.util.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class CurrentlyPlayingFetchJob(
    private val currentlyPlaying: CurrentlyPlayingPort,
) {

    @Scheduled(cron = "0/30 * * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Running scheduled currently playing fetch" }
        currentlyPlaying.enqueueUpdates()
    }

    companion object : KLogging()
}
