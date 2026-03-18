package de.chrgroth.spotify.control.domain.model

import java.util.Locale

data class ListeningStats(
    val listenedMinutesLast30Days: Long,
    val topTracksLast30Days: List<TopEntry>,
    val topArtistsLast30Days: List<TopEntry>,
) {
    val listenedMinutesLast30DaysFormatted: String get() = listenedMinutesLast30Days.formatted()
}

private fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
