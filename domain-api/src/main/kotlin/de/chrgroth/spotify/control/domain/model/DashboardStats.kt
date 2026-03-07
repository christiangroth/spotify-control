package de.chrgroth.spotify.control.domain.model

data class DashboardStats(
    val syncedPlaylists: Long,
    val totalPlaylists: Long,
    val totalPlaybackEvents: Long,
    val playbackEventsLast30Days: Long,
    val playbackEventsPerDay: List<DayCount>,
    val recentlyPlayedTracks: List<RecentlyPlayedItem>,
    val listeningStats: ListeningStats,
)
