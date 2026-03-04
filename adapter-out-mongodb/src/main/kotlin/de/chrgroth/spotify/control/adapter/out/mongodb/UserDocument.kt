package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "user")
class UserDocument {

  @BsonId
  lateinit var spotifyUserId: String
  lateinit var displayName: String
  lateinit var encryptedAccessToken: String
  lateinit var encryptedRefreshToken: String
  lateinit var tokenExpiresAt: Instant
  lateinit var createdAt: Instant
  lateinit var lastLoginAt: Instant
  var playlists: List<PlaylistInfoDocument> = emptyList()
}

class PlaylistInfoDocument {
  lateinit var spotifyPlaylistId: String
  lateinit var snapshotId: String
  lateinit var lastSnapshotIdSyncTime: Instant
  var lastSnapshotChange: Instant? = null
  lateinit var name: String
  lateinit var syncStatus: String
}
