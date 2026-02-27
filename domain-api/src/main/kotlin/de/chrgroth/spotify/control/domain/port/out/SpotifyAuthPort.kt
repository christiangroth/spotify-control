package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.DomainResult
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.SpotifyTokens

interface SpotifyAuthPort {
    fun exchangeCode(code: String): DomainResult<SpotifyTokens>
    fun getUserProfile(accessToken: AccessToken): DomainResult<SpotifyProfile>
    fun refreshToken(refreshToken: RefreshToken): DomainResult<SpotifyRefreshedTokens>
}
