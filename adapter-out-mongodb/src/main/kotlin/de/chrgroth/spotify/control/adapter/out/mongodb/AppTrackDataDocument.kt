package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_track")
class AppTrackDocument {

    @BsonId
    lateinit var id: String  // Set to trackId value; maps to MongoDB _id
    lateinit var trackTitle: String
    var albumId: String? = null
    lateinit var artistId: String
    var additionalArtistIds: List<String> = emptyList()
    var lastEnrichmentDate: Instant? = null
}
