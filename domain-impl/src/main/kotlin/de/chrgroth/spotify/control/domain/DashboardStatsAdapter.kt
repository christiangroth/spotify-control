package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.DayCount
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardStatsPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DashboardStatsAdapter(
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
    @param:ConfigProperty(name = "dashboard.recently-played.limit", defaultValue = "13")
    private val recentlyPlayedLimit: Int,
) : DashboardStatsPort {

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
        val trackMap = appTrackRepository.findByTrackIds(trackIds).associateBy { it.trackId }
        val allArtistIds = trackMap.values.flatMap { track ->
            listOfNotNull(track.artistId) + track.additionalArtistIds
        }.toSet()
        val artistMap = appArtistRepository.findByArtistIds(allArtistIds).associateBy { it.artistId }
        val recentlyPlayedTracks = recentPlaybackItems.map { playback ->
            val track = trackMap[playback.trackId]
            val trackArtistIds = listOfNotNull(track?.artistId) + (track?.additionalArtistIds ?: emptyList())
            val artistNames = trackArtistIds.mapNotNull { artistMap[it]?.artistName }
            RecentlyPlayedItem(
                spotifyUserId = playback.userId,
                trackId = playback.trackId,
                trackName = track?.trackTitle ?: playback.trackId,
                artistIds = trackArtistIds,
                artistNames = artistNames,
                playedAt = playback.playedAt,
            )
        }

        return DashboardStats(
            syncedPlaylists = syncedPlaylists,
            totalPlaylists = totalPlaylists,
            totalPlaybackEvents = total,
            playbackEventsLast30Days = last30Days,
            playbackEventsPerDay = perDay,
            recentlyPlayedTracks = recentlyPlayedTracks,
        )
    }

    companion object {
        private const val STATS_DAYS = 30
    }
}
