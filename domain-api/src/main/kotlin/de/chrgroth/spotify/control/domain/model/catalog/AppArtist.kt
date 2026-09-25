package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

/**
 * Processed artist metadata, shared across all users to avoid duplication in app_track.
 * All fields are populated by the Spotify API sync. Artists are never stored partially.
 */
data class AppArtist(
  val id: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val type: String? = null,
  val lastSync: Instant,
  val syncStatus: ArtistSyncStatus = ArtistSyncStatus.SYNC,
  val followed: Boolean = false,
  val followedSince: Instant? = null,
  val lastFollowSync: Instant? = null,
  val singularityIncluded: Boolean = false,
  // Set for a brand-new artist (analogous to ArtistSyncStatus's assumption states): the system assumes
  // singularityIncluded=false until the user confirms or overturns it on the "End of the Road" review page.
  val singularityReviewPending: Boolean = false,
  val singularityCurrentTrackAddedAt: Instant? = null,
  val singularityReviewedUntil: Instant? = null,
  val singularityCurrentTrackId: TrackId? = null,
)
