package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.util.outbox.OutboxError

interface OutboxHandlerPort {
    fun handleFetchRecentlyPlayed(): Either<OutboxError, Unit>
    fun handleUpdateUserProfiles(): Either<OutboxError, Unit>
}
