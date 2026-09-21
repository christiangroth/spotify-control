package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

data class FollowedArtistItem(
  val artistId: String,
  val artistName: String,
  val imageLink: String? = null,
  val followedSince: Instant? = null,
)

/**
 * Currently followed artists plus the timestamp of the most recent followed-artists sync run (see [FollowedArtistItem]),
 * used to show the user how stale this data might be.
 */
data class FollowedArtistsOverview(
  val artists: List<FollowedArtistItem> = emptyList(),
  val lastSyncedAt: Instant? = null,
)
