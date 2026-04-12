package de.chrgroth.spotify.control.domain.model.playback

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import kotlinx.datetime.LocalDate

data class PlaybackEventViewerResult(
  val date: LocalDate,
  val isToday: Boolean,
  val events: List<PlaybackEventEntry>,
  val artists: List<AppArtist> = emptyList(),
)
