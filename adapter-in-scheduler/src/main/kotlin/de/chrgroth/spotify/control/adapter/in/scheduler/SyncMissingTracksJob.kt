package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UseBulkFetchStatePort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingTracksJob(
    private val outboxPort: OutboxPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
    private val useBulkFetchState: UseBulkFetchStatePort,
    private val userRepository: UserRepositoryPort,
) {

    // Runs at :05 of every 3 hours (staggered with SyncMissingArtistsJob and SyncMissingAlbumsJob)
    @Scheduled(cron = "0 5 0/3 * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        if (useBulkFetchState.isUsingBulkFetch()) {
            runBulk()
        } else {
            runPerItem()
        }
    }

    private fun runBulk() {
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

    private fun runPerItem() {
        // Uses the first available user, consistent with the existing bulk sync fallback in CatalogAdapter
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping per-item SyncMissingTracks" }
            return
        }
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekTracks(BULK_LIMIT)
            if (batch.isEmpty()) break
            batch.forEach { trackId ->
                outboxPort.enqueue(DomainOutboxEvent.SyncTrackDetails(trackId, userId))
            }
            syncPoolRepository.markTracksEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued per-item SyncTrackDetails tasks for $totalEnqueued track(s) in sync pool (bulk endpoint disabled)" }
        } else {
            logger.debug { "No non-enqueued tracks in sync pool, skipping per-item SyncMissingTracks" }
        }
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
