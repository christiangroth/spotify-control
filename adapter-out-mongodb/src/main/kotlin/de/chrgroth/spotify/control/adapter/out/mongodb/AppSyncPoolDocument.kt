package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

enum class SyncPoolType { ARTIST, TRACK, ALBUM }

@MongoEntity(collection = "app_sync_pool")
class AppSyncPoolDocument {

    @BsonId
    lateinit var id: String  // Composite key: "$type:$spotifyId"; maps to MongoDB _id
    var type: SyncPoolType = SyncPoolType.ARTIST
    lateinit var spotifyId: String
    var enqueued: Boolean = false
}
