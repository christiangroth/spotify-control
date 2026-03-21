package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class MigrateToSpotifyPlaybackPartitionStarter : Starter {

    override val id = "MigrateToSpotifyPlaybackPartitionStarter-v1"

    override fun execute() {
        logger.info { "Migration MigrateToSpotifyPlaybackPartitionStarter already completed, skipping." }
    }

    companion object : KLogging()
}
