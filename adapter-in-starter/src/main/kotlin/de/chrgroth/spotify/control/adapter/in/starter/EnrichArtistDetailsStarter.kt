package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.spotify.control.util.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

/**
 * Enqueues [DomainOutboxEvent.EnrichArtistDetails] for all artists that are missing enrichment data
 * (i.e. no `lastEnrichmentDate`). This ensures that artist data required for the artist settings UI
 * (images, genres) is populated even for artists created before the enrichment pipeline was in place.
 *
 * A single user's token is used to fetch the artist details. Since artist data is shared across all
 * users (deduplication key is artistId only), one enrichment event per artist is sufficient regardless
 * of which user's credentials are used.
 */
@ApplicationScoped
@Suppress("Unused")
class EnrichArtistDetailsStarter(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val outboxPort: OutboxPort,
) : Starter {

    override val id = "EnrichArtistDetailsStarter-v1"

    override fun execute() {
        val user = userRepository.findAll().firstOrNull()
        if (user == null) {
            logger.info { "No users found, skipping artist enrichment enqueue" }
            return
        }
        val unenrichedArtists = appArtistRepository.findAll()
            .filter { it.lastEnrichmentDate == null }
        if (unenrichedArtists.isEmpty()) {
            logger.info { "All artists already enriched, nothing to enqueue" }
            return
        }
        logger.info { "Enqueueing EnrichArtistDetails for ${unenrichedArtists.size} unenriched artist(s)" }
        unenrichedArtists.forEach { artist ->
            outboxPort.enqueue(DomainOutboxEvent.EnrichArtistDetails(artist.artistId, user.spotifyUserId))
        }
    }

    companion object : KLogging()
}
