package de.chrgroth.spotify.control.domain.model.playlist

/**
 * A single violation found by a playlist check. [id] is stable across repeated check runs for the same
 * underlying track/position so that a subset of violations can be selected for a fix.
 */
data class PlaylistCheckViolation(
  val id: String,
  val message: String,
)
