package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.playback.RawPlaybackEvent
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackEventViewerRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging
import org.bson.BsonArray
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonString
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class PlaybackEventViewerRepositoryAdapter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : PlaybackEventViewerRepositoryPort {

  override fun findRecentlyPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(RECENTLY_PLAYED_COLLECTION, PLAYED_AT_FIELD, DURATION_SECONDS_FIELD, 1L, userId, from, to)

  override fun findRecentlyPartialPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(RECENTLY_PARTIAL_PLAYED_COLLECTION, PLAYED_AT_FIELD, PLAYED_SECONDS_FIELD, 1L, userId, from, to)

  override fun findCurrentlyPlaying(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(CURRENTLY_PLAYING_COLLECTION, OBSERVED_AT_FIELD, DURATION_MS_FIELD, MS_PER_SECOND, userId, from, to)

  private fun queryCollection(
    collectionName: String,
    timestampField: String,
    durationField: String,
    durationDivisor: Long,
    userId: UserId,
    from: Instant,
    to: Instant,
  ): List<RawPlaybackEvent> {
    val coll = mongoClient.getDatabase(databaseName).getCollection(collectionName, BsonDocument::class.java)
    return try {
      val filter = Filters.and(
        Filters.eq(SPOTIFY_USER_ID_FIELD, userId.value),
        Filters.gte(timestampField, from.toJavaInstant()),
        Filters.lt(timestampField, to.toJavaInstant()),
      )
      coll.find(filter).sort(Sorts.ascending(timestampField)).map { doc ->
        val bsonTs = doc[timestampField]
        val ts = if (bsonTs is BsonDateTime) {
          java.time.Instant.ofEpochMilli(bsonTs.value).toKotlinInstant()
        } else {
          Instant.DISTANT_PAST
        }
        val rawDuration = when (val d = doc[durationField]) {
          is BsonInt64 -> d.value
          is BsonInt32 -> d.value.toLong()
          else -> 0L
        }
        RawPlaybackEvent(
          timestamp = ts,
          trackId = (doc[TRACK_ID_FIELD] as? BsonString)?.value,
          trackName = (doc[TRACK_NAME_FIELD] as? BsonString)?.value,
          artistIds = (doc[ARTIST_IDS_FIELD] as? BsonArray)?.mapNotNull { (it as? BsonString)?.value } ?: emptyList(),
          artistNames = (doc[ARTIST_NAMES_FIELD] as? BsonArray)?.mapNotNull { (it as? BsonString)?.value } ?: emptyList(),
          albumId = (doc[ALBUM_ID_FIELD] as? BsonString)?.value,
          startTime = (doc[START_TIME_FIELD] as? BsonDateTime)?.let { java.time.Instant.ofEpochMilli(it.value).toKotlinInstant() },
          durationSeconds = if (rawDuration > 0) rawDuration / durationDivisor else null,
        )
      }.toList()
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to query $collectionName for playback event viewer" }
      emptyList()
    }
  }

  companion object : KLogging() {
    private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
    private const val RECENTLY_PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
    private const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    private const val PLAYED_AT_FIELD = "playedAt"
    private const val OBSERVED_AT_FIELD = "observedAt"
    private const val TRACK_ID_FIELD = "trackId"
    private const val TRACK_NAME_FIELD = "trackName"
    private const val ARTIST_IDS_FIELD = "artistIds"
    private const val ARTIST_NAMES_FIELD = "artistNames"
    private const val ALBUM_ID_FIELD = "albumId"
    private const val START_TIME_FIELD = "startTime"
    private const val DURATION_SECONDS_FIELD = "durationSeconds"
    private const val PLAYED_SECONDS_FIELD = "playedSeconds"
    private const val DURATION_MS_FIELD = "durationMs"
    private const val MS_PER_SECOND = 1000L
  }
}
