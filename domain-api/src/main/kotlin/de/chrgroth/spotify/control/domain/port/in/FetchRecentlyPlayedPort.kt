package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId

interface FetchRecentlyPlayedPort {
    fun fetchAndPersist(userId: UserId): Either<DomainError, Int>
}
