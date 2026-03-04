package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaylistSyncPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaylistSyncJob(
    private val playlistSync: PlaylistSyncPort,
) {

    @Scheduled(cron = "0 30 * * * ?")
    fun run() {
        logger.info { "Running scheduled playlist sync" }
        playlistSync.enqueueUpdates()
    }

    companion object : KLogging()
}
