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
class SyncMissingTracksJob(
    private val outboxPort: OutboxPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) {

    // Runs at :05 of every 3 hours (staggered with SyncMissingArtistsJob and SyncMissingAlbumsJob)
    @Scheduled(cron = "0 5 0/3 * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekTracks(BULK_LIMIT)
            if (batch.isEmpty()) break
            outboxPort.enqueue(DomainOutboxEvent.SyncMissingTracks(batch))
            syncPoolRepository.markTracksEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued SyncMissingTracks tasks for $totalEnqueued track(s) in sync pool" }
        } else {
            logger.debug { "No non-enqueued tracks in sync pool, skipping SyncMissingTracks" }
        }
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
