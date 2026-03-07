package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyTrackDetails
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyTrackDetailsPort {
    fun getTracks(userId: UserId, accessToken: AccessToken, trackIds: List<String>): Either<DomainError, List<SpotifyTrackDetails>>
}
