package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class CleanupNonTracksStarter(private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort) : Starter {

    override val id = "CleanupNonTracksStarter-v1"

    override fun execute() {
        val deleted = recentlyPlayedRepository.deleteNonTracks()
        logger.info { "Deleted $deleted non-track items from recently played history" }
    }

    companion object : KLogging()
}
