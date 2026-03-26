package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class DuplicateTrackIdsCheckRunner(
    private val appTrackRepository: AppTrackRepositoryPort,
) : PlaylistCheckRunner {

  override val checkId = "duplicate-track-ids"
  override val displayName = "Duplicate Track IDs"

  override fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck {
    val countByTrackId = playlist.tracks.groupingBy { it.trackId }.eachCount()
    val duplicateTrackIds = playlist.tracks
      .distinctBy { it.trackId }
      .filter { (countByTrackId[it.trackId] ?: 0) > 1 }
      .map { it.trackId }
    val appTrackById = if (duplicateTrackIds.isNotEmpty()) {
      appTrackRepository.findByTrackIds(duplicateTrackIds.toSet()).associateBy { it.id.value }
    } else {
      emptyMap()
    }
    val violations = duplicateTrackIds.map { trackId ->
      val appTrack = requireNotNull(appTrackById[trackId.value]) { "Track ${trackId.value} not found in catalog" }
      val artistName = appTrack.artistName ?: "Unknown Artist"
      "$artistName – ${appTrack.title}"
    }
    return AppPlaylistCheck(
      checkId = "$playlistId:$checkId",
      playlistId = PlaylistId(playlistId),
      lastCheck = Clock.System.now(),
      succeeded = violations.isEmpty(),
      violations = violations,
    )
  }
}
