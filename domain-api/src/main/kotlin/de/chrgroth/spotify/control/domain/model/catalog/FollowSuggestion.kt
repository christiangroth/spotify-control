package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

/**
 * A not-yet-followed artist that was recently and repeatedly among the top listened artists, suggesting the user
 * may want to follow them.
 */
data class FollowCandidate(
  val artistId: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val recentPlaybackSeconds: Long,
  val topRankWeeks: Int,
)

/**
 * A followed artist with little to no recent listening activity, suggesting the user may want to unfollow them.
 */
data class UnfollowCandidate(
  val artistId: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val followedSince: Instant?,
  val lookbackPlaybackSeconds: Long,
)

/**
 * Precomputed read model for the Follow Suggestions settings page (see ADR-0014).
 */
data class FollowSuggestionsView(
  val followCandidates: List<FollowCandidate> = emptyList(),
  val unfollowCandidates: List<UnfollowCandidate> = emptyList(),
)
