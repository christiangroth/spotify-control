package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.displayArtistName
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.aggregation.DailyPlaybackSummary
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor

@ApplicationScoped
@Suppress("Unused")
class DashboardService(
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val catalogBrowser: CatalogBrowserPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  private val managedExecutor: ManagedExecutor,
  @param:ConfigProperty(name = "dashboard.recently-played.limit")
  private val recentlyPlayedLimit: Int,
  @param:ConfigProperty(name = "dashboard.listening-stats.top-entries-limit")
  private val topEntriesLimit: Int,
) : DashboardPort {

  override fun getStats(): DashboardStats {
    val userId = currentUserResolver.userId() ?: return DashboardStats.EMPTY
    val dailyAggsFuture = managedExecutor.supplyAsync { fetchDailyAggregations(userId) }
    val totalPlaybackEventsFuture = managedExecutor.supplyAsync { aggregationRepository.sumEventCountByUser(userId) }
    val playlistMetadataFuture = managedExecutor.supplyAsync { computePlaylistMetadata(userId) }
    val playlistCheckStatsFuture = managedExecutor.supplyAsync { computePlaylistCheckStats() }
    val recentlyPlayedFuture = managedExecutor.supplyAsync { buildRecentlyPlayedTracks(userId) }
    val catalogStatsFuture = managedExecutor.supplyAsync { catalogBrowser.getCatalogStats() }

    val dailyAggs = dailyAggsFuture.join()
    val listeningStatsFuture = managedExecutor.supplyAsync { buildListeningStats(dailyAggs) }
    val playbackStats = buildPlaybackStats(dailyAggs, totalPlaybackEventsFuture.join())
    val playlistMetadata = playlistMetadataFuture.join()
    return DashboardStats(
      syncedPlaylists = playlistMetadata.syncedPlaylists,
      totalPlaylists = playlistMetadata.totalPlaylists,
      playlistCheckStats = playlistCheckStatsFuture.join(),
      totalPlaybackEvents = playbackStats.totalPlaybackEvents,
      playbackEventsLast30Days = playbackStats.playbackEventsLast30Days,
      playbackEventsPerDay = playbackStats.playbackEventsPerDay,
      recentlyPlayedTracks = recentlyPlayedFuture.join(),
      listeningStats = listeningStatsFuture.join(),
      catalogStats = catalogStatsFuture.join(),
    )
  }

  override fun getPlaybackStats(): DashboardStats {
    val userId = currentUserResolver.userId() ?: return DashboardStats.EMPTY
    return buildPlaybackStats(fetchDailyAggregations(userId), aggregationRepository.sumEventCountByUser(userId))
  }

  override fun getPlaylistMetadata(): DashboardStats {
    val userId = currentUserResolver.userId() ?: return DashboardStats.EMPTY
    return computePlaylistMetadata(userId)
  }

  override fun getRecentlyPlayed(): DashboardStats {
    val userId = currentUserResolver.userId() ?: return DashboardStats.EMPTY
    return DashboardStats.EMPTY.copy(recentlyPlayedTracks = buildRecentlyPlayedTracks(userId))
  }

  override fun getListeningStats(): DashboardStats {
    val userId = currentUserResolver.userId() ?: return DashboardStats.EMPTY
    return DashboardStats.EMPTY.copy(listeningStats = buildListeningStats(fetchDailyAggregations(userId)))
  }

  override fun getPlaylistCheckStats(): DashboardStats =
    DashboardStats.EMPTY.copy(playlistCheckStats = computePlaylistCheckStats())

  override fun getCatalogStats(): DashboardStats =
    DashboardStats.EMPTY.copy(catalogStats = catalogBrowser.getCatalogStats())

  private fun statsDateRange(): Pair<LocalDate, LocalDate> {
    val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return (today - DatePeriod(days = STATS_DAYS - 1)) to today
  }

  private fun fetchDailyAggregations(userId: UserId): List<DailyPlaybackSummary> {
    val (from, to) = statsDateRange()
    return aggregationRepository.findDailySummaryByUserAndPeriodRange(userId, from, to)
  }

  private fun buildPlaybackStats(dailyAggs: List<DailyPlaybackSummary>, total: Long): DashboardStats {
    val (_, today) = statsDateRange()
    val last30Days = dailyAggs.sumOf { it.eventCount }

    val countByDate = dailyAggs.associate { it.periodStart to it.eventCount }
    val allDays = ((STATS_DAYS - 1) downTo 0).map { today - DatePeriod(days = it) }
    val maxCount = countByDate.values.maxOrNull() ?: 1L
    val perDay = allDays.map { date ->
      val count = countByDate[date] ?: 0L
      DayCount(
        date = date,
        count = count,
        heightPercent = if (maxCount > 0) (count * 100 / maxCount).toInt() else 0,
        dateLabel = "%02d.%02d".format(date.day, date.month.ordinal + 1),
      )
    }
    return DashboardStats.EMPTY.copy(
      totalPlaybackEvents = total,
      playbackEventsLast30Days = last30Days,
      playbackEventsPerDay = perDay,
    )
  }

  private fun computePlaylistMetadata(userId: UserId): DashboardStats {
    val playlists = playlistRepository.findByUserId(userId)
    val totalPlaylists = playlists.size.toLong()
    val syncedPlaylists = playlists.count { it.syncStatus == PlaylistSyncStatus.ACTIVE }.toLong()
    return DashboardStats.EMPTY.copy(
      syncedPlaylists = syncedPlaylists,
      totalPlaylists = totalPlaylists,
    )
  }

  private fun computePlaylistCheckStats(): PlaylistCheckStats {
    val totalChecksFuture = managedExecutor.supplyAsync { playlistCheckRepository.countAll() }
    val succeededChecksFuture = managedExecutor.supplyAsync { playlistCheckRepository.countSucceeded() }
    val totalChecks = totalChecksFuture.join()
    val succeededChecks = succeededChecksFuture.join()
    return PlaylistCheckStats(
      succeededChecks = succeededChecks,
      totalChecks = totalChecks,
      allSucceeded = totalChecks == 0L || succeededChecks == totalChecks,
    )
  }

  private fun buildRecentlyPlayedTracks(userId: UserId): List<RecentlyPlayedItem> {
    val recentPlaybackItems = appPlaybackRepository.findRecentlyPlayed(userId, recentlyPlayedLimit)
    val trackIds = recentPlaybackItems.map { it.trackId }.toSet()
    val trackMap = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
    val albumIds = trackMap.values.mapNotNull { it.albumId }.toSet()
    val allArtistIds = trackMap.values.flatMap { it.allArtistIds() }.toSet()
    val albumMapFuture = managedExecutor.supplyAsync { appAlbumRepository.findByAlbumIds(albumIds).associateBy { it.id.value } }
    val artistMapFuture = managedExecutor.supplyAsync {
      appArtistRepository.findByArtistIds(allArtistIds.map { ArtistId(it) }.toSet()).associateBy { it.id.value }
    }
    val albumMap = albumMapFuture.join()
    val artistMap = artistMapFuture.join()
    return recentPlaybackItems.map { playback ->
      val track = trackMap[playback.trackId]
      val trackArtistIds = track?.allArtistIds() ?: emptyList()
      val album = track?.albumId?.let { albumMap[it.value] }
      RecentlyPlayedItem(
        spotifyUserId = playback.userId,
        trackId = TrackId(playback.trackId),
        trackName = track?.title ?: playback.trackId,
        artistIds = trackArtistIds.map { ArtistId(it) },
        artistNames = trackArtistIds.mapNotNull { artistMap[it]?.artistName },
        playedAt = playback.playedAt,
        albumName = album?.title ?: track?.albumName,
        imageLink = album?.imageLink,
        durationSeconds = playback.secondsPlayed.takeIf { it > 0 },
      )
    }
  }

  private fun buildListeningStats(dailyAggs: List<DailyPlaybackSummary>): ListeningStats {
    val secondsByTrackId = dailyAggs.flatMap { it.trackEntries }
      .groupBy { it.id }
      .mapValues { (_, entries) -> entries.sumOf { it.totalSeconds } }
    val secondsByArtistId = dailyAggs.flatMap { it.artistEntries }
      .groupBy { it.id }
      .mapValues { (_, entries) -> entries.sumOf { it.totalSeconds } }
    val listenedMinutes = dailyAggs.sumOf { it.totalPlaybackSeconds } / SECONDS_PER_MINUTE

    // resolving the album for every track played in the period is unavoidable (needed for per-album totals below),
    // but full track details (title, artists, duration, ...) are only ever displayed for the top-N tracks
    val allTrackIds = secondsByTrackId.keys.map { TrackId(it) }.toSet()
    val albumIdsByTrackId = appTrackRepository.findAlbumIdsByTrackIds(allTrackIds)
    val topTrackIds = secondsByTrackId.entries.sortedByDescending { it.value }.take(topEntriesLimit).map { TrackId(it.key) }.toSet()
    val statsTrackMap = appTrackRepository.findByTrackIds(topTrackIds).associateBy { it.id.value }

    val secondsByAlbumId = mutableMapOf<String, Long>()
    secondsByTrackId.forEach { (trackId, seconds) ->
      val albumId = albumIdsByTrackId[TrackId(trackId)]?.value ?: return@forEach
      secondsByAlbumId[albumId] = (secondsByAlbumId[albumId] ?: 0L) + seconds
    }

    // only the tracks/albums/artists that actually end up in the top-N lists below need their catalog details resolved
    val topTrackDetails = topTrackIds.mapNotNull { statsTrackMap[it.value] }
    val topAlbumIds = secondsByAlbumId.entries.sortedByDescending { it.value }.take(topEntriesLimit).map { it.key }
    val topArtistIds = secondsByArtistId.entries.sortedByDescending { it.value }.take(topEntriesLimit).map { it.key }
    val neededAlbumIds = (topTrackDetails.mapNotNull { it.albumId } + topAlbumIds.map { AlbumId(it) }).toSet()
    val neededArtistIds = (topTrackDetails.flatMap { it.allArtistIds() } + topArtistIds).map { ArtistId(it) }.toSet()

    val statsAlbumMapFuture = managedExecutor.supplyAsync { appAlbumRepository.findByAlbumIds(neededAlbumIds).associateBy { it.id.value } }
    val statsArtistMapFuture = managedExecutor.supplyAsync {
      appArtistRepository.findByArtistIds(neededArtistIds).associateBy { it.id.value }
    }
    val statsAlbumMap = statsAlbumMapFuture.join()
    val statsArtistMap = statsArtistMapFuture.join()

    val topTracks = buildTopEntries(secondsByTrackId, { statsTrackMap[it]?.title ?: it }) { id ->
      statsTrackMap[id]?.albumId?.let { statsAlbumMap[it.value]?.imageLink }
    }.map { entry ->
      val track = statsTrackMap[entry.id]
      val album = track?.albumId?.let { statsAlbumMap[it.value] }
      entry.topEntry.copy(
        artistName = track?.displayArtistName { artistId -> statsArtistMap[artistId.value]?.artistName },
        albumName = track?.albumName ?: album?.title,
        trackDurationMs = track?.durationMs,
      )
    }
    val topArtists = buildTopEntries(secondsByArtistId, { statsArtistMap[it]?.artistName ?: it }) { id ->
      statsArtistMap[id]?.imageLink
    }.map { it.topEntry }
    val topAlbums = buildTopEntries(secondsByAlbumId, { statsAlbumMap[it]?.title ?: it }) { id ->
      statsAlbumMap[id]?.imageLink
    }.map { entry ->
      entry.topEntry.copy(artistName = statsAlbumMap[entry.id]?.artistName)
    }

    return ListeningStats(
      listenedMinutesLast30Days = listenedMinutes,
      topTracksLast30Days = topTracks,
      topArtistsLast30Days = topArtists,
      topAlbumsLast30Days = topAlbums,
    )
  }

  private fun buildTopEntries(
    secondsById: Map<String, Long>,
    nameResolver: (String) -> String,
    imageResolver: ((String) -> String?)? = null,
  ): List<TopEntryView> =
    secondsById.entries
      .sortedByDescending { it.value }
      .take(topEntriesLimit)
      .map { (id, seconds) ->
        TopEntryView(
          id = id,
          topEntry = TopEntry(name = nameResolver(id), totalMinutes = seconds / SECONDS_PER_MINUTE, imageLink = imageResolver?.invoke(id)),
        )
      }

  private data class TopEntryView(val id: String, val topEntry: TopEntry)

  companion object {
    private const val STATS_DAYS = 30
    private const val SECONDS_PER_MINUTE = 60L
  }

  private fun AppTrack.allArtistIds(): List<String> = listOf(artistId.value) + additionalArtistIds.map { it.value }
}
