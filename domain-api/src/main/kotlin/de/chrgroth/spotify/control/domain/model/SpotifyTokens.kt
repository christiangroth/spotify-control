package de.chrgroth.spotify.control.domain.model

data class SpotifyTokens(val accessToken: String, val refreshToken: String, val expiresInSeconds: Int)
