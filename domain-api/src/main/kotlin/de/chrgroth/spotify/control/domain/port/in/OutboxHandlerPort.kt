package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.util.outbox.OutboxError

interface OutboxHandlerPort {
    fun handleFetchRecentlyPlayedForUser(userId: UserId): Either<OutboxError, Unit>
    fun handleUpdateUserProfileForUser(userId: UserId): Either<OutboxError, Unit>
}
