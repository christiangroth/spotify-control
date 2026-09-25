package de.chrgroth.spotify.control.domain.model.playlist

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import kotlin.time.Instant

/**
 * A not-yet-included artist that was recently and repeatedly among the top listened artists, suggesting the user
 * may want to include them on "End of the Road".
 */
data class SingularityIncludeCandidate(
  val artistId: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val recentPlaybackSeconds: Long,
  val topRankWeeks: Int,
)

/**
 * An included artist whose current "End of the Road" pick has seen little to no recent listening activity,
 * suggesting the user may want to exclude them.
 */
data class SingularityExcludeCandidate(
  val artistId: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val currentTrackAddedAt: Instant?,
  val lookbackPlaybackSeconds: Long,
)

/**
 * Precomputed read model for the Singularity Suggestions settings page (see ADR-0014).
 */
data class SingularitySuggestionsView(
  val includeCandidates: List<SingularityIncludeCandidate> = emptyList(),
  val excludeCandidates: List<SingularityExcludeCandidate> = emptyList(),
)
