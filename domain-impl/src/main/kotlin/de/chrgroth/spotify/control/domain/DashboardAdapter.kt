package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.DayCount
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ListeningStats
import de.chrgroth.spotify.control.domain.model.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.TopEntry
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DashboardAdapter(
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val catalogBrowser: CatalogBrowserPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
    @param:ConfigProperty(name = "dashboard.recently-played.limit")
    private val recentlyPlayedLimit: Int,
    @param:ConfigProperty(name = "dashboard.listening-stats.top-entries-limit")
    private val topEntriesLimit: Int,
) : DashboardPort {

    override fun getStats(userId: UserId): DashboardStats {
        val since = Clock.System.now() - STATS_DAYS.days

        val total = appPlaybackRepository.countAll(userId)
        val last30Days = appPlaybackRepository.countSince(userId, since)
        val rawPerDay = appPlaybackRepository.countPerDaySince(userId, since)

        val countByDate = rawPerDay.toMap()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
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

        val playlists = playlistRepository.findByUserId(userId)
        val totalPlaylists = playlists.size.toLong()
        val syncedPlaylists = playlists.count { it.syncStatus == PlaylistSyncStatus.ACTIVE }.toLong()

        val totalChecks = playlistCheckRepository.countAll()
        val succeededChecks = playlistCheckRepository.countSucceeded()
        val playlistCheckStats = PlaylistCheckStats(
            succeededChecks = succeededChecks,
            totalChecks = totalChecks,
            allSucceeded = totalChecks == 0L || succeededChecks == totalChecks,
        )

        return DashboardStats(
            syncedPlaylists = syncedPlaylists,
            totalPlaylists = totalPlaylists,
            playlistCheckStats = playlistCheckStats,
            totalPlaybackEvents = total,
            playbackEventsLast30Days = last30Days,
            playbackEventsPerDay = perDay,
            recentlyPlayedTracks = buildRecentlyPlayedTracks(userId),
            listeningStats = buildListeningStats(userId, since),
            catalogStats = catalogBrowser.getCatalogStats(),
        )
    }

    private fun buildRecentlyPlayedTracks(userId: UserId): List<RecentlyPlayedItem> {
        val recentPlaybackItems = appPlaybackRepository.findRecentlyPlayed(userId, recentlyPlayedLimit)
        val trackIds = recentPlaybackItems.map { it.trackId }.toSet()
        val trackMap = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
        val albumIds = trackMap.values.mapNotNull { it.albumId }.toSet()
        val albumMap = appAlbumRepository.findByAlbumIds(albumIds).associateBy { it.id.value }
        val allArtistIds = trackMap.values.flatMap { it.allArtistIds() }.toSet()
        val artistMap = appArtistRepository.findByArtistIds(allArtistIds).associateBy { it.artistId }
        return recentPlaybackItems.map { playback ->
            val track = trackMap[playback.trackId]
            val trackArtistIds = track?.allArtistIds() ?: emptyList()
            val album = track?.albumId?.let { albumMap[it.value] }
            RecentlyPlayedItem(
                spotifyUserId = playback.userId,
                trackId = playback.trackId,
                trackName = track?.title ?: playback.trackId,
                artistIds = trackArtistIds,
                artistNames = trackArtistIds.mapNotNull { artistMap[it]?.artistName },
                playedAt = playback.playedAt,
                albumName = album?.title ?: track?.albumName,
                imageUrl = album?.imageLink,
            )
        }
    }

    private fun buildListeningStats(userId: UserId, since: Instant): ListeningStats {
        val playbackItems = appPlaybackRepository.findAllSince(userId, since)
            .filter { it.secondsPlayed > 0 }

        val allTrackIds = playbackItems.map { it.trackId }.toSet()
        val statsTrackMap = appTrackRepository.findByTrackIds(allTrackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }

        val secondsByTrackId = playbackItems
            .groupBy { it.trackId }
            .mapValues { (_, items) -> items.sumOf { it.secondsPlayed } }

        val listenedMinutes = secondsByTrackId.values.sum() / SECONDS_PER_MINUTE
        val statsAlbumIds = statsTrackMap.values.mapNotNull { it.albumId }.toSet()
        val statsAlbumMap = appAlbumRepository.findByAlbumIds(statsAlbumIds).associateBy { it.id.value }
        val topTracks = buildTopEntries(secondsByTrackId, { statsTrackMap[it]?.title ?: it }) { id ->
            statsTrackMap[id]?.albumId?.let { statsAlbumMap[it.value]?.imageLink }
        }

        val statsArtistIds = statsTrackMap.values.flatMap { it.allArtistIds() }.toSet()
        val statsArtistMap = appArtistRepository.findByArtistIds(statsArtistIds).associateBy { it.artistId }

        val secondsByArtistId = mutableMapOf<String, Long>()
        for ((trackId, seconds) in secondsByTrackId) {
            val track = statsTrackMap[trackId] ?: continue
            for (artistId in track.allArtistIds()) {
                secondsByArtistId.merge(artistId, seconds, Long::plus)
            }
        }
        val topArtists = buildTopEntries(secondsByArtistId, { statsArtistMap[it]?.artistName ?: it }) { id ->
            statsArtistMap[id]?.imageLink
        }

        return ListeningStats(
            listenedMinutesLast30Days = listenedMinutes,
            topTracksLast30Days = topTracks,
            topArtistsLast30Days = topArtists,
        )
    }

    private fun buildTopEntries(
        secondsById: Map<String, Long>,
        nameResolver: (String) -> String,
        imageResolver: ((String) -> String?)? = null,
    ): List<TopEntry> =
        secondsById.entries
            .sortedByDescending { it.value }
            .take(topEntriesLimit)
            .map { (id, seconds) -> TopEntry(name = nameResolver(id), totalMinutes = seconds / SECONDS_PER_MINUTE, imageUrl = imageResolver?.invoke(id)) }

    companion object {
        private const val STATS_DAYS = 30
        private const val SECONDS_PER_MINUTE = 60L
    }
}

private fun AppTrack.allArtistIds(): List<String> = listOf(artistId.value) + additionalArtistIds.map { it.value }
