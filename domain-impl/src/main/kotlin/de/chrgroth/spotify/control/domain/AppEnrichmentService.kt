package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Shared service that upserts app_artist and app_track stubs then enqueues the enrichment
 * pipeline on the throttled `to-spotify` outbox partition:
 *
 * 1. [DomainOutboxEvent.EnrichArtistDetails] — one per artist
 * 2. [DomainOutboxEvent.EnrichTrackDetails]  — one per track; fetches full track details,
 *    updates app_track with all fields, upserts app_album, and enqueues EnrichArtistDetails
 *    for all track artists.
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
            outboxPort.enqueue(DomainOutboxEvent.EnrichTrackDetails(track.id.value, userId))
        }
    }

    companion object : KLogging()
}
