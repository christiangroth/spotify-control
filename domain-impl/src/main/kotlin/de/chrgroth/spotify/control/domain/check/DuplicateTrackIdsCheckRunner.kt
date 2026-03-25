package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
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
      appTrackRepository.findByTrackIds(duplicateTrackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
    } else {
      emptyMap()
    }
    val violations = duplicateTrackIds.map { trackId ->
      val appTrack = requireNotNull(appTrackById[trackId]) { "Track $trackId not found in catalog" }
      val artistName = appTrack.artistName ?: "Unknown Artist"
      "$artistName – ${appTrack.title}"
    }
    return AppPlaylistCheck(
      checkId = "$playlistId:$checkId",
      playlistId = playlistId,
      lastCheck = Clock.System.now(),
      succeeded = violations.isEmpty(),
      violations = violations,
    )
  }
}
