package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyAccessTokenPort {
    fun getValidAccessToken(userId: UserId): AccessToken
}
