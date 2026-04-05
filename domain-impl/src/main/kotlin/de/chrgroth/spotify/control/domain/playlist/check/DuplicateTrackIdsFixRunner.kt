package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class DuplicateTrackIdsFixRunner(
  private val spotifyPlaylist: SpotifyPlaylistPort,
) : PlaylistCheckFixRunner {

  override val checkId = "duplicate-track-ids"

  override fun runFix(userId: UserId, accessToken: AccessToken, playlistId: String, playlist: Playlist): Either<DomainError, Unit> {
    val positionsToRemoveByTrackId = mutableMapOf<String, MutableList<Int>>()
    val seenTrackIds = mutableSetOf<String>()
    playlist.tracks.forEachIndexed { index, track ->
      val trackId = track.trackId.value
      if (!seenTrackIds.add(trackId)) {
        positionsToRemoveByTrackId.getOrPut(trackId) { mutableListOf() }.add(index)
      }
    }
    if (positionsToRemoveByTrackId.isEmpty()) {
      logger.info { "No duplicate tracks found in playlist $playlistId, nothing to fix" }
      return Unit.right()
    }
    val totalToRemove = positionsToRemoveByTrackId.values.sumOf { it.size }
    logger.info { "Removing $totalToRemove duplicate track(s) from playlist $playlistId (user ${userId.value})" }
    return spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, positionsToRemoveByTrackId)
  }

  companion object : KLogging()
}
