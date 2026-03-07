package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyAlbumDetailsPort {
    fun getAlbum(userId: UserId, accessToken: AccessToken, albumId: String): Either<DomainError, AppAlbum?>
}
