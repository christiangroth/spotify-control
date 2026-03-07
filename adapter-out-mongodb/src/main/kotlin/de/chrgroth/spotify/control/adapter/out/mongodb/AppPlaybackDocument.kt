package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "app_playback")
class AppPlaybackDocument {

    @BsonId
    var id: ObjectId = ObjectId()
    lateinit var spotifyUserId: String
    lateinit var playedAt: Instant
    lateinit var trackId: String
    var secondsPlayed: Long = 0L
}
