package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxError

interface OutboxHandlerPort {
    fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): Either<OutboxError, Unit>
    fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<OutboxError, Unit>
}
