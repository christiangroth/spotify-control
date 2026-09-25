package de.chrgroth.spotify.control.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.SingularityExcludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularityIncludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularitySuggestionsView
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface SingularitySuggestionsPort {
  fun findIncludeCandidates(): List<SingularityIncludeCandidate>
  fun findExcludeCandidates(): List<SingularityExcludeCandidate>
  fun getSingularitySuggestionsView(): SingularitySuggestionsView
  fun rebuildSingularitySuggestionsView()
  fun handle(event: DomainOutboxEvent.RebuildSingularitySuggestions): Either<DomainError, Unit>
}
