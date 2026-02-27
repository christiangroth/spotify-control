package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.outbox.OutboxError
import de.chrgroth.spotify.control.domain.outbox.PollRecentlyPlayedPayload

interface OutboxHandlerPort {
    fun handlePollRecentlyPlayed(payload: PollRecentlyPlayedPayload): Either<OutboxError, Unit>
}
