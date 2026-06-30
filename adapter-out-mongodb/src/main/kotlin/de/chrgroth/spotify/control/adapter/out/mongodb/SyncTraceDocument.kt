package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "sync_trace")
class SyncTraceDocument {

  @BsonId
  lateinit var id: String  // Set to "{entityType}:{entityId}"; maps to MongoDB _id
  lateinit var entityType: String
  lateinit var entityId: String
  lateinit var causeType: String
  var causeArtistId: String? = null
  var causePlaylistId: String? = null
  var causeTrackId: String? = null
  lateinit var triggeredAt: Instant
}
