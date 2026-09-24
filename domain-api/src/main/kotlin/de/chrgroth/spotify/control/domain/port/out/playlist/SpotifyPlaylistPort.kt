package de.chrgroth.spotify.control.domain.port.out.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import kotlin.time.Instant

interface SpotifyPlaylistPort {
  fun getPlaylists(accessToken: AccessToken): Either<DomainError, List<SpotifyPlaylistItem>>
  fun getPlaylistTracks(accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist>
  fun getPlaylistTracksPage(accessToken: AccessToken, playlistId: String, pageUrl: String?): Either<DomainError, PlaylistTracksPage>
  fun getPlaylistTrackAddedAtByArtist(accessToken: AccessToken, playlistId: String): Either<DomainError, Map<ArtistId, Instant>>
  fun removePlaylistTracks(accessToken: AccessToken, playlistId: String, trackIds: List<String>): Either<DomainError, Unit>
  fun addPlaylistTracks(accessToken: AccessToken, playlistId: String, trackIds: List<String>): Either<DomainError, Unit>
  fun replacePlaylistTrack(accessToken: AccessToken, playlistId: String, oldTrackId: String, newTrackId: String, position: Int): Either<DomainError, Unit>
}
