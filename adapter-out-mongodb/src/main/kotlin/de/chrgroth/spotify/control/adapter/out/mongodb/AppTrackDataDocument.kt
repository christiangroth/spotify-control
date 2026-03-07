package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "app_track_data")
class AppTrackDataDocument {

    @BsonId
    lateinit var id: String  // Set to trackId value; maps to MongoDB _id
    var albumId: String? = null
    lateinit var artistIds: List<String>
    lateinit var trackTitle: String
    var albumTitle: String? = null
    lateinit var artistNames: List<String>
    var genres: List<String> = emptyList()
    var imageLink: String? = null
}
