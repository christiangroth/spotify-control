package de.chrgroth.spotify.control.domain.port.`in`.playback

import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import kotlinx.datetime.LocalDate

interface PlaybackEventViewerPort {
  fun getEvents(date: LocalDate): PlaybackEventViewerResult
}
