package de.chrgroth.spotify.control.domain.model

data class SpotifyRefreshedTokens(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken?,
    val expiresInSeconds: Int,
)
