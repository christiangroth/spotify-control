package de.chrgroth.spotify.control.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface UserProfilePort {
  fun getDisplayName(): String?
  fun enqueueUpdates()
  fun update(): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<DomainError, Unit>
}
