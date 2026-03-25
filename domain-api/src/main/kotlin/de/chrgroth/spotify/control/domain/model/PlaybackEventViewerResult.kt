package de.chrgroth.spotify.control.domain.model

import kotlinx.datetime.LocalDate

data class PlaybackEventViewerResult(
    val date: LocalDate,
    val isToday: Boolean,
    val events: List<PlaybackEventEntry>,
)
