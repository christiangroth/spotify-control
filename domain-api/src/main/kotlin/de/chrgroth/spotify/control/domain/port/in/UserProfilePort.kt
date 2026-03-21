package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface UserProfilePort {
    fun enqueueUpdates()
    fun update(userId: UserId): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.UpdateUserProfile): DispatchResult
}
