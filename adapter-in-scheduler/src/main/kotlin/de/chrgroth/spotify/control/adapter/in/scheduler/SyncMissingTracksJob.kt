package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingTracksJob(
    private val catalog: CatalogPort,
) {

    // Runs at :05, :15, :25, :35, :45, :55 of each hour (staggered with SyncMissingArtistsJob)
    @Scheduled(cron = "0 5/10 * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Running scheduled sync for missing tracks" }
        catalog.syncMissingTracks()
    }

    companion object : KLogging()
}
