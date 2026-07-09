package de.chrgroth.spotify.control.domain.port.out.catalog

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistAlbumsPage

interface SpotifyCatalogPort {
  fun getArtist(accessToken: AccessToken, artistId: String): Either<DomainError, AppArtist?>

  /**
   * Fetches an album's full metadata and all tracks via GET /v1/albums/{id}.
   * Used as a fallback for albums whose metadata is not yet known locally.
   */
  fun getAlbum(accessToken: AccessToken, albumId: String): Either<DomainError, AlbumSyncResult>

  /**
   * Fetches all tracks for an album whose metadata is already known, via GET /v1/albums/{id}/tracks.
   * Avoids re-fetching album metadata that was already captured from the artist discography response.
   */
  fun getAlbumTracks(accessToken: AccessToken, album: AppAlbum): Either<DomainError, List<AppTrack>>
  fun getArtistAlbumsPage(accessToken: AccessToken, artistId: String, nextUrl: String?): Either<DomainError, ArtistAlbumsPage>
}
