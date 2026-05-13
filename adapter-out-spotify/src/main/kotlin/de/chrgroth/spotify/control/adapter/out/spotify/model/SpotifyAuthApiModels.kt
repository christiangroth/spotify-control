package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("expires_in") val expiresIn: Int,
)
