package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_singularity_suggestions_view")
class SingularitySuggestionsViewDocument {

  @BsonId
  lateinit var id: String
  var includeCandidates: List<SingularityIncludeCandidateDocument> = emptyList()
  var excludeCandidates: List<SingularityExcludeCandidateDocument> = emptyList()
}

class SingularityIncludeCandidateDocument {
  lateinit var artistId: String
  lateinit var artistName: String
  var imageLink: String? = null
  var recentPlaybackSeconds: Long = 0
  var topRankWeeks: Int = 0
}

class SingularityExcludeCandidateDocument {
  lateinit var artistId: String
  lateinit var artistName: String
  var imageLink: String? = null
  var currentTrackAddedAt: Instant? = null
  var lookbackPlaybackSeconds: Long = 0
}
