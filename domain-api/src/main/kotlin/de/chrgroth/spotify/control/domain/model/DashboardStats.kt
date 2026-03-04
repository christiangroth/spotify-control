package de.chrgroth.spotify.control.domain.model

data class DashboardStats(
    val totalPlaybackEvents: Long,
    val playbackEventsLast30Days: Long,
    val playbackEventsPerDay: List<DayCount>,
    val spotifyRequestStats: List<SpotifyRequestStats>,
    val outboxPartitions: List<OutboxPartitionStats>,
)
