package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "app_sync_pool")
class AppSyncPoolDocument {

    @BsonId
    lateinit var id: String  // Composite key: "$type:$spotifyId"; maps to MongoDB _id
    lateinit var type: String  // "ARTIST" or "TRACK"
    lateinit var spotifyId: String
}
