package de.chrgroth.spotify.control.domain.model.playback

import kotlin.time.Instant

data class PlaybackEventEntry(
  val type: PlaybackEventType,
  val timestamp: Instant,
  val isWarning: Boolean,
  val trackId: String?,
  val trackName: String?,
  val artistId: String?,
  val artistName: String?,
  val albumId: String?,
  val albumName: String?,
  val startTime: Instant?,
  val durationSeconds: Long?,
)
