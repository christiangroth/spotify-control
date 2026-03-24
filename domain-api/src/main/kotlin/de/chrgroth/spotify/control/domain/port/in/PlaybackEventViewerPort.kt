package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.UserId
import kotlinx.datetime.LocalDate

interface PlaybackEventViewerPort {
    fun getEvents(userId: UserId, date: LocalDate): PlaybackEventViewerResult
}
