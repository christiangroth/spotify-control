package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that adds artist and track IDs to the sync pool for bulk processing
 * by SyncMissingArtistsJob and SyncMissingTracksJob. Entities are never stored
 * partially — the sync jobs fetch full data from the Spotify API and store it
 * via their respective repository upsertAll methods.
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

        if (artistIdsToSync.isNotEmpty()) syncPoolRepository.addArtists(artistIdsToSync)
        if (trackIdsToSync.isNotEmpty()) syncPoolRepository.addTracks(trackIdsToSync)
    }

    companion object : KLogging()
}
