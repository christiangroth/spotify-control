package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class RemoveGenreFieldsStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "RemoveGenreFieldsStarter-v1"

    override fun execute() {
        val database = mongoClient.getDatabase(databaseName)

        val artistCollection = database.getCollection(ARTIST_COLLECTION)
        val artistResult = artistCollection.updateMany(
            Filters.or(Filters.exists(GENRE_FIELD), Filters.exists(ADDITIONAL_GENRES_FIELD)),
            Updates.combine(Updates.unset(GENRE_FIELD), Updates.unset(ADDITIONAL_GENRES_FIELD)),
        )
        logger.info { "Removed genre fields from ${artistResult.modifiedCount} artist documents" }

        val albumCollection = database.getCollection(ALBUM_COLLECTION)
        val albumResult = albumCollection.updateMany(
            Filters.exists(GENRE_OVERRIDES_FIELD),
            Updates.unset(GENRE_OVERRIDES_FIELD),
        )
        logger.info { "Removed genreOverrides field from ${albumResult.modifiedCount} album documents" }
    }

    companion object : KLogging() {
        private const val ARTIST_COLLECTION = "app_artist"
        private const val ALBUM_COLLECTION = "app_album"
        private const val GENRE_FIELD = "genre"
        private const val ADDITIONAL_GENRES_FIELD = "additionalGenres"
        private const val GENRE_OVERRIDES_FIELD = "genreOverrides"
    }
}
