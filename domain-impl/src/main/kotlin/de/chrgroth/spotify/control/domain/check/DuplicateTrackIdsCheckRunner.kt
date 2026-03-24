package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.UserId
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class DuplicateTrackIdsCheckRunner : PlaylistCheckRunner {

    override val checkId = "duplicate-track-ids"
    override val displayName = "Duplicate Track IDs"

    override fun run(userId: UserId, playlistId: String, playlist: Playlist, playlistInfos: List<PlaylistInfo>): AppPlaylistCheck {
        val countByTrackId = playlist.tracks.groupingBy { it.trackId }.eachCount()
        val violations = playlist.tracks
            .distinctBy { it.trackId }
            .filter { (countByTrackId[it.trackId] ?: 0) > 1 }
            .map { track ->
                val artistName = track.artistNames.firstOrNull() ?: track.artistIds.firstOrNull() ?: "Unknown Artist"
                "$artistName – ${track.trackName}"
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
