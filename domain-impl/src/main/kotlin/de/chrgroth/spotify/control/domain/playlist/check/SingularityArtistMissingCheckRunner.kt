package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class SingularityArtistMissingCheckRunner(
  private val appArtistRepository: AppArtistRepositoryPort,
) : PlaylistCheckRunner {

  override val checkId = "singularity-artist-missing"
  override val displayName = "Singularity Artist Missing"

  override fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = playlistInfo?.type == PlaylistType.SINGULARITY

  override fun run(
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): AppPlaylistCheck {
    val artistIdsOnPlaylist = playlist.tracks.map { it.mainArtistId }.toSet()
    val violations = appArtistRepository.findAll()
      .filter { it.singularityIncluded && it.id !in artistIdsOnPlaylist }
      .map { artist -> PlaylistCheckViolation(id = artist.id.value, message = artist.artistName) }
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
