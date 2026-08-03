package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.adapter.out.mongodb.MongoQueryMetrics.Companion.SINGLETON_ID
import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.port.out.readmodel.DashboardViewRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate

@ApplicationScoped
class DashboardViewRepositoryAdapter(
  private val dashboardViewDocumentRepository: DashboardViewDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : DashboardViewRepositoryPort {

  override fun save(stats: DashboardStats) {
    mongoQueryMetrics.saveSingleton(dashboardViewDocumentRepository, "app_dashboard_view", stats.toDocument())
  }

  override fun find(): DashboardStats? =
    mongoQueryMetrics.findSingleton(dashboardViewDocumentRepository, "app_dashboard_view")?.toDomain()

  private fun DashboardViewDocument.toDomain() = DashboardStats(
    syncedPlaylists = syncedPlaylists,
    totalPlaylists = totalPlaylists,
    playlistCheckStats = PlaylistCheckStats(
      succeededChecks = playlistCheckStats.succeededChecks,
      totalChecks = playlistCheckStats.totalChecks,
      allSucceeded = playlistCheckStats.allSucceeded,
    ),
    totalPlaybackEvents = totalPlaybackEvents,
    playbackEventsLast30Days = playbackEventsLast30Days,
    playbackEventsPerDay = playbackEventsPerDay.map { entry ->
      DayCount(
        date = LocalDate.parse(entry.date),
        count = entry.count,
        heightPercent = entry.heightPercent,
        dateLabel = entry.dateLabel,
      )
    },
    recentlyPlayedTracks = recentlyPlayedTracks.map { entry ->
      RecentlyPlayedItem(
        trackId = TrackId(entry.trackId),
        trackName = entry.trackName,
        artistIds = entry.artistIds.map { ArtistId(it) },
        artistNames = entry.artistNames,
        playedAt = entry.playedAt.toKotlinInstant(),
        startTime = entry.startTime?.toKotlinInstant(),
        albumId = entry.albumId?.let { AlbumId(it) },
        albumName = entry.albumName,
        imageLink = entry.imageLink,
        durationSeconds = entry.durationSeconds,
      )
    },
    listeningStats = ListeningStats(
      listenedMinutesLast30Days = listeningStats.listenedMinutesLast30Days,
      topTracksLast30Days = listeningStats.topTracksLast30Days.map { it.toDomain() },
      topArtistsLast30Days = listeningStats.topArtistsLast30Days.map { it.toDomain() },
      topAlbumsLast30Days = listeningStats.topAlbumsLast30Days.map { it.toDomain() },
    ),
    catalogStats = CatalogStats(
      artistCount = catalogStats.artistCount,
      albumCount = catalogStats.albumCount,
      trackCount = catalogStats.trackCount,
      undecidedArtistCount = catalogStats.undecidedArtistCount,
      shallowArtistCount = catalogStats.shallowArtistCount,
    ),
  )

  private fun TopEntryDocument.toDomain() = TopEntry(
    name = name,
    totalMinutes = totalMinutes,
    imageLink = imageLink,
    artistName = artistName,
    albumName = albumName,
    trackDurationMs = trackDurationMs,
  )

  private fun DashboardStats.toDocument() = DashboardViewDocument().apply {
    id = SINGLETON_ID
    syncedPlaylists = this@toDocument.syncedPlaylists
    totalPlaylists = this@toDocument.totalPlaylists
    playlistCheckStats = PlaylistCheckStatsDocument().apply {
      succeededChecks = this@toDocument.playlistCheckStats.succeededChecks
      totalChecks = this@toDocument.playlistCheckStats.totalChecks
      allSucceeded = this@toDocument.playlistCheckStats.allSucceeded
    }
    totalPlaybackEvents = this@toDocument.totalPlaybackEvents
    playbackEventsLast30Days = this@toDocument.playbackEventsLast30Days
    playbackEventsPerDay = this@toDocument.playbackEventsPerDay.map { entry ->
      DayCountDocument().apply {
        date = entry.date.toString()
        count = entry.count
        heightPercent = entry.heightPercent
        dateLabel = entry.dateLabel
      }
    }
    recentlyPlayedTracks = this@toDocument.recentlyPlayedTracks.map { entry ->
      RecentlyPlayedItemDocument().apply {
        trackId = entry.trackId.value
        trackName = entry.trackName
        artistIds = entry.artistIds.map { it.value }
        artistNames = entry.artistNames
        playedAt = entry.playedAt.toJavaInstant()
        startTime = entry.startTime?.toJavaInstant()
        albumId = entry.albumId?.value
        albumName = entry.albumName
        imageLink = entry.imageLink
        durationSeconds = entry.durationSeconds
      }
    }
    listeningStats = ListeningStatsDocument().apply {
      listenedMinutesLast30Days = this@toDocument.listeningStats.listenedMinutesLast30Days
      topTracksLast30Days = this@toDocument.listeningStats.topTracksLast30Days.map { it.toDocument() }
      topArtistsLast30Days = this@toDocument.listeningStats.topArtistsLast30Days.map { it.toDocument() }
      topAlbumsLast30Days = this@toDocument.listeningStats.topAlbumsLast30Days.map { it.toDocument() }
    }
    catalogStats = CatalogStatsDocument().apply {
      artistCount = this@toDocument.catalogStats.artistCount
      albumCount = this@toDocument.catalogStats.albumCount
      trackCount = this@toDocument.catalogStats.trackCount
      undecidedArtistCount = this@toDocument.catalogStats.undecidedArtistCount
      shallowArtistCount = this@toDocument.catalogStats.shallowArtistCount
    }
  }

  private fun TopEntry.toDocument() = TopEntryDocument().apply {
    name = this@toDocument.name
    totalMinutes = this@toDocument.totalMinutes
    imageLink = this@toDocument.imageLink
    artistName = this@toDocument.artistName
    albumName = this@toDocument.albumName
    trackDurationMs = this@toDocument.trackDurationMs
  }
}
