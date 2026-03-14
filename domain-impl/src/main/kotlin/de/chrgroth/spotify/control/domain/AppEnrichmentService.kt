package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that upserts app_artist and app_track stubs then adds them to the sync pool
 * for bulk processing by SyncMissingArtistsJob and SyncMissingTracksJob:
 *
 * 1. Artist and track stubs are upserted to app_artist and app_track collections immediately
 *    so that playback data can reference them.
 * 2. Their IDs are added to app_sync_pool for bulk sync via the Spotify API in the next
 *    scheduled run of the sync jobs (every 10 minutes, staggered by 5 minutes).
 *
 * The sync jobs use bulk Spotify API endpoints with a fallback to per-item requests.
 *
 * When [forceSync] is false (default for automatic playback triggers), only artists and tracks
 * that have not been fully synced yet (lastSync == null) are added to the sync pool.
 * When [forceSync] is true (for manual triggers such as playlist activation or catalog resync),
 * all artists and tracks are added to the sync pool regardless of their current sync state.
 */
@ApplicationScoped
class AppSyncService(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) {

    fun upsertAndAddToSyncPool(artists: List<AppArtist>, tracks: List<AppTrack>, forceSync: Boolean = false) {
        if (artists.isEmpty() && tracks.isEmpty()) return

        val artistIdsToSync = if (!forceSync && artists.isNotEmpty()) {
            val existingWithSync = appArtistRepository.findByArtistIds(artists.map { it.artistId }.toSet())
                .filter { it.lastSync != null }
                .map { it.artistId }
                .toSet()
            artists.filter { it.artistId !in existingWithSync }.map { it.artistId }
        } else {
            artists.map { it.artistId }
        }

        val trackIdsToSync = if (!forceSync && tracks.isNotEmpty()) {
            val existingWithSync = appTrackRepository.findByTrackIds(tracks.map { it.id }.toSet())
                .filter { it.lastSync != null }
                .map { it.id.value }
                .toSet()
            tracks.filter { it.id.value !in existingWithSync }.map { it.id.value }
        } else {
            tracks.map { it.id.value }
        }

        appArtistRepository.upsertAll(artists)
        if (artistIdsToSync.isNotEmpty()) {
            syncPoolRepository.addArtists(artistIdsToSync)
        }

        appTrackRepository.upsertAll(tracks)
        if (trackIdsToSync.isNotEmpty()) {
            syncPoolRepository.addTracks(trackIdsToSync)
        }
    }

    companion object : KLogging()
}
