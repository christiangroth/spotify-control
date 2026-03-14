package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingAlbumsJob(
    private val outboxPort: OutboxPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) {

    // Runs at :10 of every 3 hours (staggered with SyncMissingArtistsJob and SyncMissingTracksJob)
    @Scheduled(cron = "0 10 0/3 * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekAlbums(ALBUM_BULK_LIMIT)
            if (batch.isEmpty()) break
            outboxPort.enqueue(DomainOutboxEvent.SyncMissingAlbums(batch))
            syncPoolRepository.markAlbumsEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued SyncMissingAlbums tasks for $totalEnqueued album(s) in sync pool" }
        } else {
            logger.debug { "No non-enqueued albums in sync pool, skipping SyncMissingAlbums" }
        }
    }

    companion object : KLogging() {
        private const val ALBUM_BULK_LIMIT = 10
    }
}
