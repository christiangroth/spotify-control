package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

// Single-user migration cleanup (see ADR-0008): the app_playback,
// app_playback_aggregation, spotify_playlist and spotify_playlist_metadata collections used to key documents by
// "${spotifyUserId}:...", and spotify_currently_playing/spotify_recently_played/spotify_recently_partial_played
// carried a spotifyUserId field, all to disambiguate between users. Since there is now only ever one user, this
// rewrites existing documents to drop that prefix/field. MongoDB doesn't allow changing an existing _id in place,
// so the composite-key collections are migrated via insert-then-delete rather than a simple field update.
@ApplicationScoped
@Suppress("Unused")
class SimplifySingleUserSchemaStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "SimplifySingleUserSchemaStarter-v1"

  override fun execute() {
    val database = mongoClient.getDatabase(databaseName)
    unsetSpotifyUserId(database, CURRENTLY_PLAYING_COLLECTION)
    unsetSpotifyUserId(database, RECENTLY_PLAYED_COLLECTION)
    unsetSpotifyUserId(database, RECENTLY_PARTIAL_PLAYED_COLLECTION)
    rewriteIds(database, APP_PLAYBACK_COLLECTION) { doc -> doc.getDate(PLAYED_AT_FIELD).time.toString() }
    rewriteIds(database, APP_PLAYBACK_AGGREGATION_COLLECTION) { doc -> "${doc.getString(TYPE_FIELD)}:${doc.getString(PERIOD_START_FIELD)}" }
    rewriteIds(database, PLAYLIST_COLLECTION) { doc -> doc.getString(SPOTIFY_PLAYLIST_ID_FIELD) }
    rewriteIds(database, PLAYLIST_METADATA_COLLECTION) { doc -> doc.getString(SPOTIFY_PLAYLIST_ID_FIELD) }
  }

  private fun unsetSpotifyUserId(database: MongoDatabase, collectionName: String) {
    val result = database.getCollection(collectionName)
      .updateMany(Filters.exists(SPOTIFY_USER_ID_FIELD), Updates.unset(SPOTIFY_USER_ID_FIELD))
    logger.info { "Removed spotifyUserId from ${result.modifiedCount} document(s) in $collectionName" }
  }

  private fun rewriteIds(database: MongoDatabase, collectionName: String, newId: (Document) -> String) {
    val collection = database.getCollection(collectionName)
    val staleDocs = collection.find(Filters.exists(SPOTIFY_USER_ID_FIELD)).toList()
    if (staleDocs.isEmpty()) {
      logger.info { "No $collectionName document(s) with a legacy spotifyUserId found" }
      return
    }
    val rewritten = staleDocs.map { doc ->
      Document(doc).apply {
        put("_id", newId(doc))
        remove(SPOTIFY_USER_ID_FIELD)
      }
    }
    collection.insertMany(rewritten)
    collection.deleteMany(Filters.`in`("_id", staleDocs.map { it.getString("_id") }))
    logger.info { "Rewrote ${rewritten.size} $collectionName document id(s) to drop the spotifyUserId prefix" }
  }

  private fun MongoDatabase.getCollection(name: String): MongoCollection<Document> = getCollection(name, Document::class.java)

  companion object : KLogging() {
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
    private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
    private const val RECENTLY_PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
    private const val APP_PLAYBACK_COLLECTION = "app_playback"
    private const val APP_PLAYBACK_AGGREGATION_COLLECTION = "app_playback_aggregation"
    private const val PLAYLIST_COLLECTION = "spotify_playlist"
    private const val PLAYLIST_METADATA_COLLECTION = "spotify_playlist_metadata"
    private const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    private const val SPOTIFY_PLAYLIST_ID_FIELD = "spotifyPlaylistId"
    private const val PLAYED_AT_FIELD = "playedAt"
    private const val TYPE_FIELD = "type"
    private const val PERIOD_START_FIELD = "periodStart"
  }
}
