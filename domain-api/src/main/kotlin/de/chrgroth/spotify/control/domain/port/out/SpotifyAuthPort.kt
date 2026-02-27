package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.SpotifyTokens

interface SpotifyAuthPort {
    fun exchangeCode(code: String): Either<DomainError, SpotifyTokens>
    fun getUserProfile(accessToken: AccessToken): Either<DomainError, SpotifyProfile>
    fun refreshToken(refreshToken: RefreshToken): Either<DomainError, SpotifyRefreshedTokens>
}
