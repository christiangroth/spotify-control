package de.chrgroth.spotify.control.domain.model.playlist

/**
 * An artist's eligibility for the "End of the Road" playlist, reviewed and set manually since there is no
 * external source that could derive it automatically (see `singularityIncluded` on `AppArtist`).
 */
data class SingularityArtistReview(
  val artistId: String,
  val artistName: String,
  val imageLink: String?,
  val singularityIncluded: Boolean,
)
