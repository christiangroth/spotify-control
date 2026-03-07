package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyArtistDetails
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyArtistDetailsPort {
    fun getArtists(userId: UserId, accessToken: AccessToken, artistIds: List<String>): Either<DomainError, List<SpotifyArtistDetails>>
}
