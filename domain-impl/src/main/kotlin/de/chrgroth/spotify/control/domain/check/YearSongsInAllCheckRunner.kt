package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistId
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class YearSongsInAllCheckRunner(
    private val playlistRepository: PlaylistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
) : PlaylistCheckRunner {

    override val checkId = "year-songs-in-all"
    override val displayName = "Year Songs In All"

    override fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = playlistInfo?.type == PlaylistType.YEAR

    override fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck {
        val allPlaylistInfo = allPlaylistInfos.find { it.type == PlaylistType.ALL }
        val allPlaylist = allPlaylistInfo?.let { playlistRepository.findByUserIdAndPlaylistId(userId, it.spotifyPlaylistId) }
        val violations = if (allPlaylist == null) {
            emptyList()
        } else {
            val allTrackIds = allPlaylist.tracks.map { it.trackId }.toSet()
            val missingTrackIds = playlist.tracks
                .filter { it.trackId !in allTrackIds }
                .distinctBy { it.trackId }
                .map { it.trackId }
            val appTrackById = if (missingTrackIds.isNotEmpty()) {
                appTrackRepository.findByTrackIds(missingTrackIds.toSet()).associateBy { it.id.value }
            } else {
                emptyMap()
            }
            missingTrackIds.map { trackId ->
                val appTrack = requireNotNull(appTrackById[trackId.value]) { "Track ${trackId.value} not found in catalog" }
                val artistName = appTrack.artistName ?: "Unknown Artist"
                "$artistName – ${appTrack.title}"
            }
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
