package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "computed_recently_played")
class ComputedRecentlyPlayedDocument {

  @BsonId
  var id: ObjectId = ObjectId()
  lateinit var spotifyUserId: String
  lateinit var trackId: String
  lateinit var trackName: String
  lateinit var artistIds: List<String>
  lateinit var artistNames: List<String>
  lateinit var playedAt: Instant
  var playedMs: Long = 0L
}
