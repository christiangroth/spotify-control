package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigrateEntityFieldsStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "MigrateEntityFieldsStarter-v1"

    override fun execute() {
        migrateTrackTitle()
        migrateAlbumTitle()
        migrateArtistGenres()
    }

    private fun migrateTrackTitle() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(TRACK_COLLECTION)
        val result = collection.updateMany(
            Filters.exists(TRACK_TITLE_OLD_FIELD),
            Updates.rename(TRACK_TITLE_OLD_FIELD, TITLE_FIELD),
        )
        logger.info { "Migrated ${result.modifiedCount} tracks: renamed '$TRACK_TITLE_OLD_FIELD' to '$TITLE_FIELD'" }
    }

    private fun migrateAlbumTitle() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(ALBUM_COLLECTION)
        val result = collection.updateMany(
            Filters.exists(ALBUM_TITLE_OLD_FIELD),
            Updates.rename(ALBUM_TITLE_OLD_FIELD, TITLE_FIELD),
        )
        logger.info { "Migrated ${result.modifiedCount} albums: renamed '$ALBUM_TITLE_OLD_FIELD' to '$TITLE_FIELD'" }
    }

    private fun migrateArtistGenres() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(ARTIST_COLLECTION)
        val pipeline = listOf(
            Document(
                "\$set",
                Document(
                    mapOf(
                        GENRE_NEW_FIELD to Document("\$first", "\$$GENRES_OLD_FIELD"),
                        ADDITIONAL_GENRES_NEW_FIELD to Document(
                            "\$cond",
                            Document(
                                mapOf(
                                    "if" to Document("\$gt", listOf(Document("\$size", "\$$GENRES_OLD_FIELD"), 1)),
                                    "then" to Document(
                                        "\$slice",
                                        listOf(
                                            "\$$GENRES_OLD_FIELD",
                                            1,
                                            Document("\$subtract", listOf(Document("\$size", "\$$GENRES_OLD_FIELD"), 1)),
                                        ),
                                    ),
                                    "else" to "\$\$REMOVE",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            Document("\$unset", GENRES_OLD_FIELD),
        )
        val result = collection.updateMany(Filters.exists(GENRES_OLD_FIELD), pipeline)
        logger.info { "Migrated ${result.modifiedCount} artists: '$GENRES_OLD_FIELD' array to '$GENRE_NEW_FIELD' + '$ADDITIONAL_GENRES_NEW_FIELD'" }
    }

    companion object : KLogging() {
        private const val TRACK_COLLECTION = "app_track"
        private const val ALBUM_COLLECTION = "app_album"
        private const val ARTIST_COLLECTION = "app_artist"
        private const val TITLE_FIELD = "title"
        private const val TRACK_TITLE_OLD_FIELD = "trackTitle"
        private const val ALBUM_TITLE_OLD_FIELD = "albumTitle"
        private const val GENRES_OLD_FIELD = "genres"
        private const val GENRE_NEW_FIELD = "genre"
        private const val ADDITIONAL_GENRES_NEW_FIELD = "additionalGenres"
    }
}
