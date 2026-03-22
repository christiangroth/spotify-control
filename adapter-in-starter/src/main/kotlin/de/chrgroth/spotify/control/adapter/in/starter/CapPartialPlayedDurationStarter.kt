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
class CapPartialPlayedDurationStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "CapPartialPlayedDurationStarter-v1"

    override fun execute() {
        val db = mongoClient.getDatabase(databaseName)
        val partialPlayedCollection = db.getCollection(PARTIAL_PLAYED_COLLECTION)
        val trackCollection = db.getCollection(TRACK_COLLECTION)

        val allDocs = partialPlayedCollection.find().toList()
        logger.info { "Checking ${allDocs.size} partial played documents for excessive playedSeconds" }

        var updated = 0
        var skipped = 0
        var errors = 0

        for (doc in allDocs) {
            val trackId = doc.getString(TRACK_ID_FIELD)
            val playedSeconds = doc.getLong(PLAYED_SECONDS_FIELD)
            if (playedSeconds == null) {
                skipped++
                continue
            }

            val trackDoc = trackCollection.find(Filters.eq("_id", trackId)).firstOrNull()
            val durationMs = trackDoc?.getLong(DURATION_MS_FIELD)
            when {
                trackDoc == null -> {
                    logger.error { "No app_track document found for trackId=$trackId, cannot cap playedSeconds" }
                    errors++
                }
                durationMs == null -> {
                    logger.error { "app_track document for trackId=$trackId has no durationMs, cannot cap playedSeconds" }
                    errors++
                }
                else -> {
                    val maxSeconds = durationMs / MS_PER_SECOND
                    if (playedSeconds > maxSeconds) {
                        partialPlayedCollection.updateOne(
                            Filters.eq("_id", doc["_id"]),
                            Updates.set(PLAYED_SECONDS_FIELD, maxSeconds),
                        )
                        updated++
                    } else {
                        skipped++
                    }
                }
            }
        }

        logger.info { "Capped playedSeconds for $updated partial played documents ($skipped unchanged, $errors errors)" }
    }

    companion object : KLogging() {
        private const val PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
        private const val TRACK_COLLECTION = "app_track"
        private const val PLAYED_SECONDS_FIELD = "playedSeconds"
        private const val DURATION_MS_FIELD = "durationMs"
        private const val TRACK_ID_FIELD = "trackId"
        private const val MS_PER_SECOND = 1000L
    }
}
