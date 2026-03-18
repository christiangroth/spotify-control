package de.chrgroth.spotify.control.domain.model

import java.util.Locale

data class DashboardStats(
    val syncedPlaylists: Long,
    val totalPlaylists: Long,
    val playlistCheckStats: PlaylistCheckStats,
    val totalPlaybackEvents: Long,
    val playbackEventsLast30Days: Long,
    val playbackEventsPerDay: List<DayCount>,
    val recentlyPlayedTracks: List<RecentlyPlayedItem>,
    val listeningStats: ListeningStats,
    val catalogStats: CatalogStats,
) {
    val syncedPlaylistsFormatted: String get() = syncedPlaylists.formatted()
    val totalPlaylistsFormatted: String get() = totalPlaylists.formatted()
    val totalPlaybackEventsFormatted: String get() = totalPlaybackEvents.formatted()
    val playbackEventsLast30DaysFormatted: String get() = playbackEventsLast30Days.formatted()
}

private fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
