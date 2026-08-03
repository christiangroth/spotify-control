package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_dashboard_view")
class DashboardViewDocument {

  @BsonId
  lateinit var id: String
  var syncedPlaylists: Long = 0L
  var totalPlaylists: Long = 0L
  lateinit var playlistCheckStats: PlaylistCheckStatsDocument
  var totalPlaybackEvents: Long = 0L
  var playbackEventsLast30Days: Long = 0L
  lateinit var playbackEventsPerDay: List<DayCountDocument>
  lateinit var recentlyPlayedTracks: List<RecentlyPlayedItemDocument>
  lateinit var listeningStats: ListeningStatsDocument
  lateinit var catalogStats: CatalogStatsDocument
}

class PlaylistCheckStatsDocument {
  var succeededChecks: Long = 0L
  var totalChecks: Long = 0L
  var allSucceeded: Boolean = true
}

class DayCountDocument {
  lateinit var date: String
  var count: Long = 0L
  var heightPercent: Int = 0
  lateinit var dateLabel: String
}

class RecentlyPlayedItemDocument {
  lateinit var trackId: String
  lateinit var trackName: String
  lateinit var artistIds: List<String>
  lateinit var artistNames: List<String>
  lateinit var playedAt: Instant
  var startTime: Instant? = null
  var albumId: String? = null
  var albumName: String? = null
  var imageLink: String? = null
  var durationSeconds: Long? = null
}

class ListeningStatsDocument {
  var listenedMinutesLast30Days: Long = 0L
  lateinit var topTracksLast30Days: List<TopEntryDocument>
  lateinit var topArtistsLast30Days: List<TopEntryDocument>
  lateinit var topAlbumsLast30Days: List<TopEntryDocument>
}

class TopEntryDocument {
  lateinit var name: String
  var totalMinutes: Long = 0L
  var imageLink: String? = null
  var artistName: String? = null
  var albumName: String? = null
  var trackDurationMs: Long? = null
}

class CatalogStatsDocument {
  var artistCount: Long = 0L
  var albumCount: Long = 0L
  var trackCount: Long = 0L
  var undecidedArtistCount: Long = 0L
  var shallowArtistCount: Long = 0L
}
