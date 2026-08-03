package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_playlist_check_dashboard")
class PlaylistCheckDashboardDocument {

  @BsonId
  lateinit var id: String
  lateinit var displayName: String
  lateinit var checks: List<PlaylistCheckDashboardEntryDocument>
  lateinit var playlistNameById: Map<String, String>
}

class PlaylistCheckDashboardEntryDocument {
  lateinit var checkId: String
  lateinit var playlistId: String
  lateinit var lastCheck: Instant
  var succeeded: Boolean = false
  lateinit var violations: List<PlaylistCheckViolationDocument>
}
