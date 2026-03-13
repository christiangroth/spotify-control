package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class DropCollectionsStarter(private val databaseMigration: DatabaseMigrationPort) : Starter {

    override val id = "DropCollectionsStarter-v1"

    override fun execute() {
        databaseMigration.dropCollectionIfExists("recently_partial_played")
        logger.info { "Collection drop migration completed for recently_partial_played" }
    }

    companion object : KLogging()
}
