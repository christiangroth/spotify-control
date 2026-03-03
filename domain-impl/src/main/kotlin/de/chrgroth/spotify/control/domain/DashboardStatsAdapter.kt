package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.DayCount
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardStatsPort
import de.chrgroth.spotify.control.domain.port.out.OutboxInfoPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@ApplicationScoped
@Suppress("Unused")
class DashboardStatsAdapter(
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val outboxInfo: OutboxInfoPort,
) : DashboardStatsPort {

    override fun getStats(userId: UserId): DashboardStats {
        val since = Clock.System.now() - 30.days

        val total = recentlyPlayedRepository.countAll(userId)
        val last30Days = recentlyPlayedRepository.countSince(userId, since)
        val rawPerDay = recentlyPlayedRepository.countPerDaySince(userId, since)

        val maxCount = rawPerDay.maxOfOrNull { it.second } ?: 1L
        val perDay = rawPerDay.map { (date, count) ->
            DayCount(
                date = date,
                count = count,
                heightPercent = (count * 100 / maxCount).toInt(),
            )
        }

        return DashboardStats(
            totalPlaybackEvents = total,
            playbackEventsLast30Days = last30Days,
            playbackEventsPerDay = perDay,
            outboxPartitions = outboxInfo.getPartitionStats(),
        )
    }
}
