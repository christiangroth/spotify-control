package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RenameCollectionsStarter(private val databaseMigration: DatabaseMigrationPort) : Starter {

    override val id = "RenameCollectionsStarter-v1"

    override fun execute() {
        databaseMigration.renameCollectionIfExists("user", "app_user")
        databaseMigration.renameCollectionIfExists("playlist", "spotify_playlist")
        databaseMigration.renameCollectionIfExists("playlist_metadata", "spotify_playlist_metadata")
        databaseMigration.renameCollectionIfExists("recently_played", "spotify_recently_played")
        databaseMigration.renameCollectionIfExists("currently_playing", "spotify_currently_playing")
        logger.info { "Collection rename migration completed" }
    }

    companion object : KLogging()
}
