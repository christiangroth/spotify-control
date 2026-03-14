package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.DayCount
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ListeningStats
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.TopEntry
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
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
    private val catalogBrowser: CatalogBrowserPort,
    private val playlistRepository: PlaylistRepositoryPort,
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

        val recentPlaybackItems = appPlaybackRepository.findRecentlyPlayed(userId, recentlyPlayedLimit)
        val trackIds = recentPlaybackItems.map { it.trackId }.toSet()
        val trackMap = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
        val allArtistIds = trackMap.values.flatMap { it.allArtistIds() }.toSet()
        val artistMap = appArtistRepository.findByArtistIds(allArtistIds).associateBy { it.artistId }
        val recentlyPlayedTracks = recentPlaybackItems.map { playback ->
            val track = trackMap[playback.trackId]
            val trackArtistIds = track?.allArtistIds() ?: emptyList()
            val artistNames = trackArtistIds.mapNotNull { artistMap[it]?.artistName }
            RecentlyPlayedItem(
                spotifyUserId = playback.userId,
                trackId = playback.trackId,
                trackName = track?.title ?: playback.trackId,
                artistIds = trackArtistIds,
                artistNames = artistNames,
                playedAt = playback.playedAt,
            )
        }

        val listeningStats = buildListeningStats(userId, since)

        val catalogStats = catalogBrowser.getCatalogStats()

        return DashboardStats(
            syncedPlaylists = syncedPlaylists,
            totalPlaylists = totalPlaylists,
            totalPlaybackEvents = total,
            playbackEventsLast30Days = last30Days,
            playbackEventsPerDay = perDay,
            recentlyPlayedTracks = recentlyPlayedTracks,
            listeningStats = listeningStats,
            catalogStats = catalogStats,
        )
    }

    private fun buildListeningStats(userId: UserId, since: Instant): ListeningStats {
        val playbackItems = appPlaybackRepository.findAllSince(userId, since)

        val listenedMinutes = playbackItems.sumOf { it.secondsPlayed } / SECONDS_PER_MINUTE

        val secondsByTrackId = playbackItems
            .groupBy { it.trackId }
            .mapValues { (_, items) -> items.sumOf { it.secondsPlayed } }

        val statsTrackIds = secondsByTrackId.keys
        val statsTrackMap = appTrackRepository.findByTrackIds(statsTrackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }

        val topTracks = secondsByTrackId.entries
            .sortedByDescending { it.value }
            .take(topEntriesLimit)
            .map { (trackId, seconds) ->
                TopEntry(
                    name = statsTrackMap[trackId]?.title ?: trackId,
                    totalMinutes = seconds / SECONDS_PER_MINUTE,
                )
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

        val topArtists = secondsByArtistId.entries
            .sortedByDescending { it.value }
            .take(topEntriesLimit)
            .map { (artistId, seconds) ->
                TopEntry(
                    name = statsArtistMap[artistId]?.artistName ?: artistId,
                    totalMinutes = seconds / SECONDS_PER_MINUTE,
                )
            }

        val secondsByGenre = mutableMapOf<String, Long>()
        for ((artistId, seconds) in secondsByArtistId) {
            val artist = statsArtistMap[artistId]
            val genres = listOfNotNull(artist?.genre) + (artist?.additionalGenres ?: emptyList())
            for (genre in genres) {
                secondsByGenre.merge(genre, seconds, Long::plus)
            }
        }

        val topGenres = secondsByGenre.entries
            .sortedByDescending { it.value }
            .take(topEntriesLimit)
            .map { (genre, seconds) ->
                TopEntry(
                    name = genre,
                    totalMinutes = seconds / SECONDS_PER_MINUTE,
                )
            }

        return ListeningStats(
            listenedMinutesLast30Days = listenedMinutes,
            topTracksLast30Days = topTracks,
            topArtistsLast30Days = topArtists,
            topGenresLast30Days = topGenres,
        )
    }

    companion object {
        private const val STATS_DAYS = 30
        private const val SECONDS_PER_MINUTE = 60L
    }
}

private fun AppTrack.allArtistIds(): List<String> = listOf(artistId.value) + additionalArtistIds.map { it.value }
