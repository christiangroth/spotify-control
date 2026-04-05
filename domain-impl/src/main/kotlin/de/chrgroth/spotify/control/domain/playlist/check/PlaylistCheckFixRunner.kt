package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId

interface PlaylistCheckFixRunner {
  val checkId: String
  fun runFix(userId: UserId, accessToken: AccessToken, playlistId: String, playlist: Playlist): Either<DomainError, Unit>
}
