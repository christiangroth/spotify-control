package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyRecentlyPlayedTrack

interface SpotifyRecentlyPlayedPort {
    fun getRecentlyPlayed(accessToken: AccessToken): Either<DomainError, List<SpotifyRecentlyPlayedTrack>>
}
