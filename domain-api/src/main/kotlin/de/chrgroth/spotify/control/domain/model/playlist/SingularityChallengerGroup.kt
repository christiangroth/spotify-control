package de.chrgroth.spotify.control.domain.model.playlist

/**
 * An artist that already has a current track on the "End of the Road" playlist and one or more open
 * challenger tracks staged on "Start of the Road", awaiting an accept/discard decision.
 */
data class SingularityChallengerGroup(
  val artistId: String,
  val artistName: String,
  val currentTrack: SingularityTrackInfo,
  val challengers: List<SingularityTrackInfo>,
)

data class SingularityTrackInfo(
  val trackId: String,
  val title: String,
)
