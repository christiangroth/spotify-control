package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_playlist_settings_view")
class PlaylistSettingsViewDocument {

  @BsonId
  lateinit var id: String
  lateinit var entries: List<PlaylistSettingsEntryDocument>
}

class PlaylistSettingsEntryDocument {
  lateinit var spotifyPlaylistId: String
  lateinit var snapshotId: String
  lateinit var lastSnapshotIdSyncTime: Instant
  lateinit var name: String
  lateinit var syncStatus: String
  var type: String? = null
  var lastSyncTime: Instant? = null
  var numberOfTracks: Int? = null
  var numberOfArtists: Int? = null
  var numberOfMissingArtists: Int? = null
}
