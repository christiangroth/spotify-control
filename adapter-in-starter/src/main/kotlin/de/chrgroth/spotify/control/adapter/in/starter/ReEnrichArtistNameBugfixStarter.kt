package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ReEnrichArtistNameBugfixStarter(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val outboxPort: OutboxPort,
) : Starter {

    override val id = "ReEnrichArtistNameBugfix-v1"

    override fun execute() {
        val artists = appArtistRepository.findWithImageLinkAndBlankName()
        if (artists.isEmpty()) {
            logger.info { "No artists with imageLink but missing name found, nothing to re-enrich" }
            return
        }
        val user = userRepository.findAll().firstOrNull()
        if (user == null) {
            logger.warn { "No users found, cannot enqueue artist re-sync for ${artists.size} artist(s)" }
            return
        }
        logger.info { "Enqueuing re-sync for ${artists.size} artist(s) with imageLink but missing name" }
        artists.forEach { artist ->
            outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artist.artistId, user.spotifyUserId))
        }
    }

    companion object : KLogging()
}
