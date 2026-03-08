package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that upserts app_artist and app_track stubs then enqueues the full
 * three-stage enrichment pipeline on the throttled `to-spotify` outbox partition:
 *
 * 1. [DomainOutboxEvent.EnrichArtistDetails] — one per artist
 * 2. [DomainOutboxEvent.EnrichTrackDetails]  — one per track; auto-chains to EnrichAlbumDetails
 *    when the albumId is fetched from Spotify
 * 3. [DomainOutboxEvent.EnrichAlbumDetails]  — one per albumId that is already known in
 *    app_track; needed because EnrichTrackDetails is skipped for pre-enriched tracks, so
 *    album enrichment would otherwise be missed for those tracks.
 *
 * All enrichment handlers implement "skip if already enriched" so duplicate events are harmless.
 */
@ApplicationScoped
class AppEnrichmentService(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val outboxPort: OutboxPort,
) {

    fun upsertAndEnqueueEnrichment(artists: List<AppArtist>, tracks: List<AppTrack>, userId: UserId) {
        if (artists.isEmpty() && tracks.isEmpty()) return

        appArtistRepository.upsertAll(artists)
        artists.forEach { artist ->
            outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails(artist.artistId, userId))
        }

        appTrackRepository.upsertAll(tracks)
        tracks.forEach { track ->
            outboxPort.enqueue(DomainOutboxEvent.EnrichTrackDetails(track.trackId, userId))
        }

        // EnrichTrackDetails is skipped when albumId is already populated.  For those tracks the
        // album enrichment must be triggered directly so previously-synced albums are not missed.
        if (tracks.isNotEmpty()) {
            val trackIds = tracks.map { it.trackId }.toSet()
            appTrackRepository.findByTrackIds(trackIds)
                .mapNotNull { it.albumId }
                .forEach { albumId ->
                    logger.debug { "Enqueueing EnrichAlbumDetails for already-known album $albumId (user ${userId.value})" }
                    outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId, userId))
                }
        }
    }

    companion object : KLogging()
}
