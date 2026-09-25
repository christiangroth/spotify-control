package de.chrgroth.spotify.control.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.SingularityArtistReview
import de.chrgroth.spotify.control.domain.model.playlist.SingularityChallengerGroup
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface SingularityPort {
  fun getChallengerGroups(): List<SingularityChallengerGroup>
  fun acceptChallenger(artistId: String, trackId: String): Either<DomainError, Unit>
  fun discardChallenger(artistId: String, trackId: String): Either<DomainError, Unit>
  fun getArtistsForReview(): List<SingularityArtistReview>
  fun setArtistIncluded(artistId: String, included: Boolean): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.AcceptSingularityChallenger): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.DiscardSingularityChallenger): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.ReconcileSingularityTracking): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.DetectSingularityChallenger): Either<DomainError, Unit>
}
