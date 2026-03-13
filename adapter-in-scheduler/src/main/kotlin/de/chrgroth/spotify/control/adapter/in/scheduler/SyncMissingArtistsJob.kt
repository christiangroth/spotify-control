package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingArtistsJob(
    private val catalog: CatalogPort,
) {

    // Runs at :00, :10, :20, :30, :40, :50 of each hour (staggered with SyncMissingTracksJob)
    @Scheduled(cron = "0 0/10 * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Running scheduled sync for missing artists" }
        catalog.syncMissingArtists()
    }

    companion object : KLogging()
}
