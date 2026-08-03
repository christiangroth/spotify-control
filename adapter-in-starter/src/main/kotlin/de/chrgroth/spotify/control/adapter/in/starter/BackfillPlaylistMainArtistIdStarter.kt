package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

// Backfills tracks.mainArtistId (see #867 Slow Queries) on existing spotify_playlist documents so
// PlaylistRepositoryAdapter.findDistinctArtistIds() can project the stored field instead of recomputing the
// main artist per track via an aggregation over every track on every read. mainArtistId must never be null
// (see PlaylistTrack.mainArtistId), so tracks with no artist at all are dropped rather than backfilled with null.
@ApplicationScoped
@Suppress("Unused")
class BackfillPlaylistMainArtistIdStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "BackfillPlaylistMainArtistIdStarter-v1"

  override fun execute() {
    val collection = mongoClient.getDatabase(databaseName).getCollection(PLAYLIST_COLLECTION)
    val result = collection.updateMany(
      Filters.exists("$TRACKS_FIELD.0"),
      listOf(
        Document(
          "\$set",
          Document(
            TRACKS_FIELD,
            Document(
              "\$map",
              Document("input", "\$$TRACKS_FIELD")
                .append("as", "track")
                .append(
                  "in",
                  Document(
                    "\$mergeObjects",
                    listOf(
                      "\$\$track",
                      Document(MAIN_ARTIST_ID_FIELD, Document("\$arrayElemAt", listOf(Document("\$ifNull", listOf("\$\$track.artistIds", emptyList<String>())), 0))),
                    ),
                  ),
                ),
            ),
          ),
        ),
        Document(
          "\$set",
          Document(
            TRACKS_FIELD,
            Document(
              "\$filter",
              Document("input", "\$$TRACKS_FIELD")
                .append("as", "track")
                .append("cond", Document("\$ne", listOf("\$\$track.$MAIN_ARTIST_ID_FIELD", null))),
            ),
          ),
        ),
      ),
    )
    logger.info { "Backfilled mainArtistId on ${result.modifiedCount} playlist document(s), dropping tracks with no artist" }
  }

  companion object : KLogging() {
    private const val PLAYLIST_COLLECTION = "spotify_playlist"
    private const val TRACKS_FIELD = "tracks"
    private const val MAIN_ARTIST_ID_FIELD = "mainArtistId"
  }
}
