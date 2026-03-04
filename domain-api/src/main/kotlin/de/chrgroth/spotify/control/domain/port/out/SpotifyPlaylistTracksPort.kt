package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyPlaylistTracksPort {
    fun getPlaylistTracks(userId: UserId, accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist>
}
