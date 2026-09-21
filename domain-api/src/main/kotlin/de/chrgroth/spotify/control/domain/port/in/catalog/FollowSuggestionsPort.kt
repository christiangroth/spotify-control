package de.chrgroth.spotify.control.domain.port.`in`.catalog

import de.chrgroth.spotify.control.domain.model.catalog.FollowCandidate
import de.chrgroth.spotify.control.domain.model.catalog.UnfollowCandidate

interface FollowSuggestionsPort {
  fun findFollowCandidates(): List<FollowCandidate>
  fun findUnfollowCandidates(): List<UnfollowCandidate>
}
