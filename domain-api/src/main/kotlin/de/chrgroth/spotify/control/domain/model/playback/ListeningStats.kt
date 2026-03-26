package de.chrgroth.spotify.control.domain.model.playback

import de.chrgroth.spotify.control.domain.util.formatted

data class ListeningStats(
    val listenedMinutesLast30Days: Long,
    val topTracksLast30Days: List<TopEntry>,
    val topArtistsLast30Days: List<TopEntry>,
) {
    val listenedMinutesLast30DaysFormatted: String get() = listenedMinutesLast30Days.formatted()
}
