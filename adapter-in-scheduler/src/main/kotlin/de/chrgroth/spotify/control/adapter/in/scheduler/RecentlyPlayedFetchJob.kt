package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedFetchJob(
    private val playback: PlaybackPort,
) {

    @Scheduled(cron = "0 0/10 * * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
    fun run() {
        logger.info { "Running scheduled recently played fetch" }
        playback.enqueueFetchRecentlyPlayed()
    }

    companion object : KLogging()
}
