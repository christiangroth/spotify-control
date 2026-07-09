package de.chrgroth.spotify.control.domain.model.playback

import kotlin.time.Instant

data class AppPlaybackItem(
  val playedAt: Instant,
  val trackId: String,
  val secondsPlayed: Long,
)
