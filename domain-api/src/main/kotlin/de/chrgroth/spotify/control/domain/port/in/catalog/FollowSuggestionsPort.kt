package de.chrgroth.spotify.control.domain.port.`in`.catalog

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.FollowCandidate
import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView
import de.chrgroth.spotify.control.domain.model.catalog.UnfollowCandidate
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface FollowSuggestionsPort {
  fun findFollowCandidates(): List<FollowCandidate>
  fun findUnfollowCandidates(): List<UnfollowCandidate>
  fun getFollowSuggestionsView(): FollowSuggestionsView
  fun rebuildFollowSuggestionsView()
  fun handle(event: DomainOutboxEvent.RebuildFollowSuggestions): Either<DomainError, Unit>
}
