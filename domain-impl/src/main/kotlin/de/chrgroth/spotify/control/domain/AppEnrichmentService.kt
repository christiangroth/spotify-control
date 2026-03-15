package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UseBulkFetchStatePort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that adds artist, track and album IDs to the sync pool and
 * immediately enqueues the corresponding outbox sync events, so that enrichment
 * happens as soon as items are discovered rather than waiting for a periodic job.
 * Entities are never stored partially — the sync handlers fetch full data from
 * the Spotify API and store it via their respective repository upsertAll methods.
 *
 * When [forceSync] is false (default for automatic playback triggers), only IDs that
 * are not yet present in the catalog are added to the sync pool.
 * When [forceSync] is true (for manual triggers such as playlist activation or catalog resync),
 * all IDs are added to the sync pool regardless of their current state.
 */
@ApplicationScoped
class AppSyncService(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
    private val outboxPort: OutboxPort,
    private val useBulkFetchState: UseBulkFetchStatePort,
    private val userRepository: UserRepositoryPort,
) {

    fun addToSyncPool(artistIds: List<String>, trackIds: List<String>, forceSync: Boolean = false) {
        if (artistIds.isEmpty() && trackIds.isEmpty()) return

        val artistIdsToSync = if (forceSync || artistIds.isEmpty()) artistIds
        else {
            val existingIds = appArtistRepository.findByArtistIds(artistIds.toSet()).map { it.artistId }.toSet()
            artistIds.filter { it !in existingIds }
        }

        val trackIdsToSync = if (forceSync || trackIds.isEmpty()) trackIds
        else {
            val existingIds = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet()).map { it.id.value }.toSet()
            trackIds.filter { it !in existingIds }
        }

        if (artistIdsToSync.isNotEmpty()) {
            syncPoolRepository.addArtists(artistIdsToSync)
            enqueueSyncArtistsFromPool()
        }
        if (trackIdsToSync.isNotEmpty()) {
            syncPoolRepository.addTracks(trackIdsToSync)
            enqueueSyncTracksFromPool()
        }
    }

    fun addAlbumsToSyncPool(albumIds: List<String>) {
        if (albumIds.isEmpty()) return
        syncPoolRepository.addAlbums(albumIds)
        enqueueSyncAlbumsFromPool()
    }

    private fun enqueueSyncArtistsFromPool() {
        var totalEnqueued = 0
        if (useBulkFetchState.isUsingBulkFetch()) {
            while (true) {
                val batch = syncPoolRepository.peekArtists(BULK_LIMIT)
                if (batch.isEmpty()) break
                outboxPort.enqueue(DomainOutboxEvent.SyncMissingArtists(batch))
                syncPoolRepository.markArtistsEnqueued(batch)
                totalEnqueued += batch.size
            }
        } else {
            val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
            if (userId == null) {
                logger.debug { "No users available, skipping artist sync enqueueing" }
                return
            }
            while (true) {
                val batch = syncPoolRepository.peekArtists(BULK_LIMIT)
                if (batch.isEmpty()) break
                batch.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
                syncPoolRepository.markArtistsEnqueued(batch)
                totalEnqueued += batch.size
            }
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued sync tasks for $totalEnqueued artist(s) from sync pool" }
        }
    }

    private fun enqueueSyncTracksFromPool() {
        var totalEnqueued = 0
        if (useBulkFetchState.isUsingBulkFetch()) {
            while (true) {
                val batch = syncPoolRepository.peekTracks(BULK_LIMIT)
                if (batch.isEmpty()) break
                outboxPort.enqueue(DomainOutboxEvent.SyncMissingTracks(batch))
                syncPoolRepository.markTracksEnqueued(batch)
                totalEnqueued += batch.size
            }
        } else {
            val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
            if (userId == null) {
                logger.debug { "No users available, skipping track sync enqueueing" }
                return
            }
            while (true) {
                val batch = syncPoolRepository.peekTracks(BULK_LIMIT)
                if (batch.isEmpty()) break
                batch.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncTrackDetails(it, userId)) }
                syncPoolRepository.markTracksEnqueued(batch)
                totalEnqueued += batch.size
            }
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued sync tasks for $totalEnqueued track(s) from sync pool" }
        }
    }

    private fun enqueueSyncAlbumsFromPool() {
        var totalEnqueued = 0
        while (true) {
            val batch = syncPoolRepository.peekAlbums(ALBUM_BULK_LIMIT)
            if (batch.isEmpty()) break
            batch.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncMissingAlbums(it)) }
            syncPoolRepository.markAlbumsEnqueued(batch)
            totalEnqueued += batch.size
        }
        if (totalEnqueued > 0) {
            logger.info { "Enqueued sync tasks for $totalEnqueued album(s) from sync pool" }
        }
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
        private const val ALBUM_BULK_LIMIT = 50
    }
}
