package de.chrgroth.spotify.control.domain.port.out.catalog

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistAlbumsPage
import de.chrgroth.spotify.control.domain.model.user.UserId

interface SpotifyCatalogPort {
  fun getArtist(userId: UserId, accessToken: AccessToken, artistId: String): Either<DomainError, AppArtist?>

  /**
   * Fetches an album's full metadata and all tracks via GET /v1/albums/{id}.
   * Used as a fallback for albums whose metadata is not yet known locally.
   */
  fun getAlbum(userId: UserId, accessToken: AccessToken, albumId: String): Either<DomainError, AlbumSyncResult>

  /**
   * Fetches all tracks for an album whose metadata is already known, via GET /v1/albums/{id}/tracks.
   * Avoids re-fetching album metadata that was already captured from the artist discography response.
   */
  fun getAlbumTracks(userId: UserId, accessToken: AccessToken, album: AppAlbum): Either<DomainError, List<AppTrack>>
  fun getArtistAlbumsPage(userId: UserId, accessToken: AccessToken, artistId: String, nextUrl: String?): Either<DomainError, ArtistAlbumsPage>
}
