package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_api_token")
class ApiTokenDocument {

  @BsonId
  lateinit var id: String
  lateinit var name: String
  lateinit var tokenHash: String
  lateinit var tokenPrefix: String
  var scopes: List<String> = emptyList()
  var active: Boolean = false
  lateinit var createdAt: Instant
  var lastUsedAt: Instant? = null
}
