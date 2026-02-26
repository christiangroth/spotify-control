package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.SpotifyTokens

interface SpotifyAuthPort {
    fun exchangeCode(code: String): SpotifyTokens
    fun getUserProfile(accessToken: String): SpotifyProfile
    fun refreshToken(refreshToken: String): SpotifyRefreshedTokens
}
