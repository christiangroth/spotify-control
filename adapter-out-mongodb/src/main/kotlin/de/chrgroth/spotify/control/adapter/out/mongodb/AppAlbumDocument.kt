package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_album")
class AppAlbumDocument {

    @BsonId
    lateinit var id: String  // Set to albumId value; maps to MongoDB _id
    var albumTitle: String? = null
    var imageLink: String? = null
    var genres: List<String> = emptyList()
    var artistId: String? = null
    var lastEnrichmentDate: Instant? = null
}
