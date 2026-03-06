package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant

interface SpotifyRecentlyPlayedPort {
    fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant? = null): Either<DomainError, List<RecentlyPlayedItem>>
}
