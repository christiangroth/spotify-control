package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoCompanionBase
import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntityBase
import org.bson.types.ObjectId
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

class RecentlyPlayedDocument : PanacheMongoEntityBase() {

    companion object : PanacheMongoCompanionBase<RecentlyPlayedDocument, ObjectId>

    @BsonId
    var id: ObjectId = ObjectId()
    lateinit var spotifyUserId: String
    lateinit var trackId: String
    lateinit var trackName: String
    lateinit var artistNames: List<String>
    lateinit var playedAt: Instant
}
