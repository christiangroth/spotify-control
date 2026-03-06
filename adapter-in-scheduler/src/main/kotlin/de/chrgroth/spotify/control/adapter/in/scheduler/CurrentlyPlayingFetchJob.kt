package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.spotify.control.util.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class CurrentlyPlayingFetchJob(
    private val currentlyPlaying: CurrentlyPlayingPort,
) {

    @Scheduled(cron = "5,25,45 * * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        currentlyPlaying.enqueueUpdates()
    }
}
