package de.chrgroth.spotify.control.domain.model

import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.util.formatted

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

    companion object {
        val EMPTY = DashboardStats(
            syncedPlaylists = 0L,
            totalPlaylists = 0L,
            playlistCheckStats = PlaylistCheckStats(succeededChecks = 0L, totalChecks = 0L, allSucceeded = true),
            totalPlaybackEvents = 0L,
            playbackEventsLast30Days = 0L,
            playbackEventsPerDay = emptyList(),
            recentlyPlayedTracks = emptyList(),
            listeningStats = ListeningStats(listenedMinutesLast30Days = 0L, topTracksLast30Days = emptyList(), topArtistsLast30Days = emptyList()),
            catalogStats = CatalogStats(artistCount = 0L, albumCount = 0L, trackCount = 0L),
        )
    }
}
