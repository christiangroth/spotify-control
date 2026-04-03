package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import kotlin.math.abs
import mu.KLogging
import org.bson.Document
import org.bson.types.ObjectId
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigratePlaybackStartTimesStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigratePlaybackStartTimesStarter-v1"

  override fun execute() {
    val database = mongoClient.getDatabase(databaseName)

    // Step 1: Backfill startTime in spotify_currently_playing as observedAt - progressMs
    val cpResult = database.getCollection(CURRENTLY_PLAYING_COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(START_TIME_FIELD, Document("\$subtract", listOf("\$$CP_OBSERVED_AT_FIELD", "\$$CP_PROGRESS_MS_FIELD"))),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${cpResult.modifiedCount} currently playing documents" }

    // Step 2: Backfill startTime in spotify_recently_partial_played as playedAt - playedSeconds*1000
    val rppResult = database.getCollection(PARTIAL_PLAYED_COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(
              START_TIME_FIELD,
              Document(
                "\$subtract",
                listOf(
                  "\$$RPP_PLAYED_AT_FIELD",
                  Document("\$multiply", listOf("\$$RPP_PLAYED_SECONDS_FIELD", 1000L)),
                ),
              ),
            ),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${rppResult.modifiedCount} recently partial played documents" }

    // Step 3: Delete partial played documents that are superseded by a recently played entry.
    // Build a map of recently-played start times: (spotifyUserId, trackId) -> List<Instant>
    val recentlyPlayedStartTimes = mutableMapOf<Pair<String, String>, MutableList<java.time.Instant>>()
    for (doc in database.getCollection(RECENTLY_PLAYED_COLLECTION).find(Filters.exists(START_TIME_FIELD))) {
      val userId = doc.getString(SPOTIFY_USER_ID_FIELD) ?: continue
      val trackId = doc.getString(TRACK_ID_FIELD) ?: continue
      val startTime = doc.getDate(START_TIME_FIELD)?.toInstant() ?: continue
      recentlyPlayedStartTimes.getOrPut(userId to trackId) { mutableListOf() }.add(startTime)
    }

    if (recentlyPlayedStartTimes.isEmpty()) {
      logger.info { "No recently played documents with startTime found, skipping deduplication" }
      return
    }

    // Find partial plays whose startTime matches a recently played entry within tolerance
    val partialIdsToDelete = mutableListOf<ObjectId>()
    val appPlaybackIdsToDelete = mutableListOf<String>()
    for (doc in database.getCollection(PARTIAL_PLAYED_COLLECTION).find(Filters.exists(START_TIME_FIELD))) {
      val userId = doc.getString(SPOTIFY_USER_ID_FIELD) ?: continue
      val trackId = doc.getString(TRACK_ID_FIELD) ?: continue
      val partialStartTime = doc.getDate(START_TIME_FIELD)?.toInstant() ?: continue
      val playedAt = doc.getDate(RPP_PLAYED_AT_FIELD)?.toInstant() ?: continue
      val docId = doc.getObjectId("_id") ?: continue

      val recentlyPlayedForTrack = recentlyPlayedStartTimes[userId to trackId] ?: continue
      val isDuplicate = recentlyPlayedForTrack.any { rpStartTime ->
        abs(java.time.Duration.between(rpStartTime, partialStartTime).toSeconds()) <= DEDUP_TOLERANCE_SECONDS
      }
      if (isDuplicate) {
        partialIdsToDelete.add(docId)
        appPlaybackIdsToDelete.add("$userId:${playedAt.toEpochMilli()}")
      }
    }

    if (partialIdsToDelete.isNotEmpty()) {
      database.getCollection(PARTIAL_PLAYED_COLLECTION).deleteMany(Filters.`in`("_id", partialIdsToDelete))
      database.getCollection(APP_PLAYBACK_COLLECTION).deleteMany(Filters.`in`("_id", appPlaybackIdsToDelete))
      logger.info { "Deleted ${partialIdsToDelete.size} duplicate partial play(s) and ${appPlaybackIdsToDelete.size} app playback entry/entries" }
    } else {
      logger.info { "No duplicate partial plays found" }
    }
  }

  companion object : KLogging() {
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
    private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
    private const val PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
    private const val APP_PLAYBACK_COLLECTION = "app_playback"
    private const val START_TIME_FIELD = "startTime"
    private const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    private const val TRACK_ID_FIELD = "trackId"
    private const val CP_OBSERVED_AT_FIELD = "observedAt"
    private const val CP_PROGRESS_MS_FIELD = "progressMs"
    private const val RPP_PLAYED_AT_FIELD = "playedAt"
    private const val RPP_PLAYED_SECONDS_FIELD = "playedSeconds"
    private const val DEDUP_TOLERANCE_SECONDS = 8L
  }
}
