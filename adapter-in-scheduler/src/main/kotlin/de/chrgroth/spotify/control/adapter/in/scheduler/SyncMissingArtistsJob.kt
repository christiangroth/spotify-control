package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SyncPoolStatePort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SyncMissingArtistsJob(
    private val outboxPort: OutboxPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
    private val syncPoolState: SyncPoolStatePort,
    private val userRepository: UserRepositoryPort,
) {

    // Runs at :00 of every 3 hours (staggered with SyncMissingTracksJob and SyncMissingAlbumsJob)
    @Scheduled(cron = "0 0 0/3 * * ?", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        if (syncPoolState.isUsingSyncPool()) {
            runBulk()
        } else {
            runPerItem()
        }
    }

    private fun runBulk() {
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekArtists(BULK_LIMIT)
            if (batch.isEmpty()) break
            outboxPort.enqueue(DomainOutboxEvent.SyncMissingArtists(batch))
            syncPoolRepository.markArtistsEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued SyncMissingArtists tasks for $totalEnqueued artist(s) in sync pool" }
        } else {
            logger.debug { "No non-enqueued artists in sync pool, skipping SyncMissingArtists" }
        }
    }

    private fun runPerItem() {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping per-item SyncMissingArtists" }
            return
        }
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekArtists(BULK_LIMIT)
            if (batch.isEmpty()) break
            batch.forEach { artistId ->
                outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId, userId))
            }
            syncPoolRepository.markArtistsEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued per-item SyncArtistDetails tasks for $totalEnqueued artist(s) in sync pool (bulk endpoint disabled)" }
        } else {
            logger.debug { "No non-enqueued artists in sync pool, skipping per-item SyncMissingArtists" }
        }
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
