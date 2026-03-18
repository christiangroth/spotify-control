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
class BackfillRecentlyPlayedDurationStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "BackfillRecentlyPlayedDurationStarter-v1"

    override fun execute() {
        val db = mongoClient.getDatabase(databaseName)
        val recentlyPlayedCollection = db.getCollection(RECENTLY_PLAYED_COLLECTION)
        val trackCollection = db.getCollection(TRACK_COLLECTION)

        val docsWithoutDuration = recentlyPlayedCollection
            .find(Filters.exists(DURATION_SECONDS_FIELD, false))
            .toList()
        logger.info { "Found ${docsWithoutDuration.size} recently played documents without durationSeconds" }

        var updated = 0
        var errors = 0

        for (doc in docsWithoutDuration) {
            val trackId = doc.getString(TRACK_ID_FIELD)
            val trackDoc = trackCollection.find(Filters.eq("_id", trackId)).firstOrNull()
            val durationMs = trackDoc?.getLong(DURATION_MS_FIELD)
            when {
                trackDoc == null -> {
                    logger.error { "No app_track document found for trackId=$trackId, cannot backfill durationSeconds" }
                    errors++
                }
                durationMs == null -> {
                    logger.error { "app_track document for trackId=$trackId has no durationMs, cannot backfill durationSeconds" }
                    errors++
                }
                else -> {
                    recentlyPlayedCollection.updateOne(
                        Filters.eq("_id", doc["_id"]),
                        Updates.set(DURATION_SECONDS_FIELD, durationMs / MS_PER_SECOND),
                    )
                    updated++
                }
            }
        }

        logger.info { "Backfilled durationSeconds for $updated recently played documents ($errors errors)" }
    }

    companion object : KLogging() {
        private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
        private const val TRACK_COLLECTION = "app_track"
        private const val DURATION_SECONDS_FIELD = "durationSeconds"
        private const val DURATION_MS_FIELD = "durationMs"
        private const val TRACK_ID_FIELD = "trackId"
        private const val MS_PER_SECOND = 1000L
    }
}
