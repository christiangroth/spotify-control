package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId

interface RecentlyPlayedPort {
    fun enqueueUpdates()
    fun update(userId: UserId): Either<DomainError, Unit>
}
