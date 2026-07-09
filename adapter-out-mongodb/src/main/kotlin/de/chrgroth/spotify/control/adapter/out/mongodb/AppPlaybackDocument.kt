package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_playback")
class AppPlaybackDocument {

  /**
   * Single key: "${playedAt.toEpochMilli()}"
   * Single-user application: playedAt alone is a natural unique identifier for a playback event.
   */
  @BsonId
  lateinit var id: String
  lateinit var playedAt: Instant
  lateinit var trackId: String
  var secondsPlayed: Long = 0L
}
