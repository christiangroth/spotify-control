package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.AccessToken

interface SpotifyAccessTokenPort {
  fun getValidAccessToken(): AccessToken
}
