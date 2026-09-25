package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class SingularityArtistExcludedCheckRunner(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
) : PlaylistCheckRunner {

  override val checkId = "singularity-artist-excluded"
  override val displayName = "Singularity Artist Excluded"

  override fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = playlistInfo?.type == PlaylistType.SINGULARITY

  override fun run(
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): AppPlaylistCheck {
    val artistIds = playlist.tracks.map { it.mainArtistId }.toSet()
    val artistById = if (artistIds.isNotEmpty()) {
      appArtistRepository.findByArtistIds(artistIds).associateBy { it.id }
    } else {
      emptyMap()
    }
    val excludedTracks = playlist.tracks.filter { artistById[it.mainArtistId]?.singularityIncluded == false }
    val appTrackById = if (excludedTracks.isNotEmpty()) {
      appTrackRepository.findByTrackIds(excludedTracks.map { it.trackId }.toSet()).associateBy { it.id.value }
    } else {
      emptyMap()
    }
    val violations = excludedTracks
      .map { track ->
        val artistName = artistById[track.mainArtistId]?.artistName ?: track.mainArtistId.value
        val trackTitle = appTrackById[track.trackId.value]?.title ?: track.trackId.value
        PlaylistCheckViolation(id = track.trackId.value, message = "$artistName – $trackTitle")
      }
      .sortedBy { it.message }
    return AppPlaylistCheck(
      checkId = "$playlistId:$checkId",
      playlistId = PlaylistId(playlistId),
      lastCheck = Clock.System.now(),
      succeeded = violations.isEmpty(),
      violations = violations,
    )
  }
}
