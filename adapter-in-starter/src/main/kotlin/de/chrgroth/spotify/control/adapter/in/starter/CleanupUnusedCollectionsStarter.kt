package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class CleanupUnusedCollectionsStarter(private val databaseMigration: DatabaseMigrationPort) : Starter {

    override val id = "CleanupUnusedCollectionsStarter-v1"

    override fun execute() {
        databaseMigration.dropCollectionIfExists("recently_partial_played")
        databaseMigration.dropCollectionIfExists("spotify_recently_partial_played")
        logger.info { "Unused collection cleanup completed" }
    }

    companion object : KLogging()
}
