package de.chrgroth.spotify.control.domain.model.playback

import kotlin.time.Instant

data class RawPlaybackEvent(
  val timestamp: Instant,
  val trackId: String?,
  val trackName: String?,
  val artistIds: List<String>,
  val artistNames: List<String>,
  val albumId: String?,
  val startTime: Instant?,
  val durationSeconds: Long?,
)
