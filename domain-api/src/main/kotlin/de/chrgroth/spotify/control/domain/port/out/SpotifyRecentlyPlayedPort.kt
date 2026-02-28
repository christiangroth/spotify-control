package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyRecentlyPlayedPort {
    fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken): Either<DomainError, List<RecentlyPlayedItem>>
}
