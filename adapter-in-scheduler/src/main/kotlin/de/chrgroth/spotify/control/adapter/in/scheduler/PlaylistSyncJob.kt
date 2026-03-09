package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaylistSyncJob(
    private val playlist: PlaylistPort,
) {

    @Scheduled(cron = "0 30 * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Running scheduled playlist sync" }
        playlist.enqueueUpdates()
    }

    companion object : KLogging()
}
