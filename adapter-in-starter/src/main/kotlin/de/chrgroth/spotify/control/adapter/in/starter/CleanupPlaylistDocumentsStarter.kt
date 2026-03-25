package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class CleanupPlaylistDocumentsStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "CleanupPlaylistDocumentsStarter-v1"

    override fun execute() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(PLAYLIST_COLLECTION)
        val result = collection.updateMany(
            Document(),
            Updates.combine(
                Updates.unset(SNAPSHOT_ID_FIELD),
                Updates.unset(TRACKS_TRACK_NAME_FIELD),
                Updates.unset(TRACKS_ARTIST_NAMES_FIELD),
            ),
        )
        logger.info { "Cleaned up ${result.modifiedCount} playlist document(s): removed $SNAPSHOT_ID_FIELD, $TRACKS_TRACK_NAME_FIELD, $TRACKS_ARTIST_NAMES_FIELD" }
    }

    companion object : KLogging() {
        private const val PLAYLIST_COLLECTION = "spotify_playlist"
        private const val SNAPSHOT_ID_FIELD = "snapshotId"
        private const val TRACKS_TRACK_NAME_FIELD = "tracks.$[].trackName"
        private const val TRACKS_ARTIST_NAMES_FIELD = "tracks.$[].artistNames"
    }
}
