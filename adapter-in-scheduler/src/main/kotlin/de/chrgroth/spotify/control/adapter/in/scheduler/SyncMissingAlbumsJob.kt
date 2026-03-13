package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingAlbumsJob(
    private val outboxPort: OutboxPort,
) {

    // Runs at :08, :18, :28, :38, :48, :58 of each hour (staggered with SyncMissingArtistsJob and SyncMissingTracksJob)
    @Scheduled(cron = "0 8/10 * * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Enqueuing SyncMissingAlbums" }
        outboxPort.enqueue(DomainOutboxEvent.SyncMissingAlbums())
    }

    companion object : KLogging()
}
