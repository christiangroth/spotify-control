package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.DayCount
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardStatsPort
import de.chrgroth.spotify.control.domain.port.out.OutboxInfoPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

@ApplicationScoped
@Suppress("Unused")
class DashboardStatsAdapter(
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val outboxInfo: OutboxInfoPort,
    private val outgoingRequestStats: OutgoingRequestStatsPort,
    private val playlistRepository: PlaylistRepositoryPort,
) : DashboardStatsPort {

    override fun getStats(userId: UserId): DashboardStats {
        val since = Clock.System.now() - STATS_DAYS.days

        val total = recentlyPlayedRepository.countAll(userId)
        val last30Days = recentlyPlayedRepository.countSince(userId, since)
        val rawPerDay = recentlyPlayedRepository.countPerDaySince(userId, since)

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

        return DashboardStats(
            syncedPlaylists = syncedPlaylists,
            totalPlaylists = totalPlaylists,
            totalPlaybackEvents = total,
            playbackEventsLast30Days = last30Days,
            playbackEventsPerDay = perDay,
            outgoingRequestStats = outgoingRequestStats.getRequestStats(),
            outboxPartitions = outboxInfo.getPartitionStats(),
        )
    }

    companion object {
        private const val STATS_DAYS = 30
    }
}
