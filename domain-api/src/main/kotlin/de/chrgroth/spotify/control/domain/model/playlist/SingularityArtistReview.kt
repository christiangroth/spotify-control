package de.chrgroth.spotify.control.domain.model.playlist

/**
 * A newly discovered artist still awaiting a one-time manual decision on "End of the Road" eligibility
 * (see `singularityReviewPending` on `AppArtist`). Always starts out assumed excluded, since there is no
 * external source that could derive inclusion automatically.
 */
data class SingularityArtistReview(
  val artistId: String,
  val artistName: String,
  val imageLink: String?,
)
