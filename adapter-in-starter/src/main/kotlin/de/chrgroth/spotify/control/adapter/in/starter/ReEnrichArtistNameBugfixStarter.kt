package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ReEnrichArtistNameBugfixStarter(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) : Starter {

    override val id = "ReEnrichArtistNameBugfix-v1"

    override fun execute() {
        val artists = appArtistRepository.findWithImageLinkAndBlankName()
        if (artists.isEmpty()) {
            logger.info { "No artists with imageLink but missing name found, nothing to re-enrich" }
            return
        }
        logger.info { "Adding ${artists.size} artist(s) with imageLink but missing name to sync pool" }
        syncPoolRepository.addArtists(artists.map { it.artistId })
    }

    companion object : KLogging()
}
