package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that upserts app_artist and app_track stubs then adds them to the sync pool
 * for bulk processing by [SyncMissingArtistsJob] and [SyncMissingTracksJob]:
 *
 * 1. Artist and track stubs are upserted to app_artist and app_track collections immediately
 *    so that playback data can reference them.
 * 2. Their IDs are added to app_sync_pool for bulk sync via the Spotify API in the next
 *    scheduled run of the sync jobs (every 10 minutes, staggered by 5 minutes).
 *
 * The sync jobs use bulk Spotify API endpoints with a fallback to per-item requests.
 */
@ApplicationScoped
class AppSyncService(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) {

    fun upsertAndAddToSyncPool(artists: List<AppArtist>, tracks: List<AppTrack>, userId: UserId) {
        if (artists.isEmpty() && tracks.isEmpty()) return

        appArtistRepository.upsertAll(artists)
        if (artists.isNotEmpty()) {
            syncPoolRepository.addArtists(artists.map { it.artistId })
        }

        appTrackRepository.upsertAll(tracks)
        if (tracks.isNotEmpty()) {
            syncPoolRepository.addTracks(tracks.map { it.id.value })
        }
    }

    companion object : KLogging()
}
