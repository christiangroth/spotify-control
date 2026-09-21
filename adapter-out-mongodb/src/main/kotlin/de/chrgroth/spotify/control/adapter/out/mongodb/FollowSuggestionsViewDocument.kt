package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_follow_suggestions_view")
class FollowSuggestionsViewDocument {

  @BsonId
  lateinit var id: String
  var followCandidates: List<FollowCandidateDocument> = emptyList()
  var unfollowCandidates: List<UnfollowCandidateDocument> = emptyList()
}

class FollowCandidateDocument {
  lateinit var artistId: String
  lateinit var artistName: String
  var imageLink: String? = null
  var recentPlaybackSeconds: Long = 0
  var topRankWeeks: Int = 0
}

class UnfollowCandidateDocument {
  lateinit var artistId: String
  lateinit var artistName: String
  var imageLink: String? = null
  var followedSince: Instant? = null
  var lookbackPlaybackSeconds: Long = 0
}
