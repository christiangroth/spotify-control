package de.chrgroth.spotify.control.domain.port.out

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.TrackSyncResult
import de.chrgroth.spotify.control.domain.model.UserId

interface SpotifyCatalogPort {
    fun getArtist(userId: UserId, accessToken: AccessToken, artistId: String): Either<DomainError, AppArtist?>
    fun getAlbum(userId: UserId, accessToken: AccessToken, albumId: String): Either<DomainError, List<TrackSyncResult>>
}
