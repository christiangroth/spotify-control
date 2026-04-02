package de.chrgroth.spotify.control.domain.model.catalog

/**
 * Controls whether an artist's tracks are included in app_playback processing.
 *
 * - [UNDECIDED]: Default state for newly discovered artists. Treated the same as [ACTIVE] for processing,
 *   but shown separately in the UI to allow explicit user decision.
 * - [ACTIVE]: Artist tracks are included in playback processing.
 * - [INACTIVE]: Artist tracks are excluded from playback processing. All existing app_playback documents
 *   for this artist's tracks are deleted when the artist is set to this status.
 */
enum class ArtistPlaybackProcessingStatus {
  UNDECIDED,
  ACTIVE,
  INACTIVE,
}
