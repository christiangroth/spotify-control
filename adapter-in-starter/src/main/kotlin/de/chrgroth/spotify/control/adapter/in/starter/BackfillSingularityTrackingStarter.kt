package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// One-time backfill for #934: initializes singularityCurrentTrackAddedAt/singularityReviewedUntil for artists that
// already have a track on the "End of the Road" playlist, since neither field existed before this. Fetches added_at
// live from Spotify because the locally synced Playlist model does not persist it.
@ApplicationScoped
@Suppress("Unused")
class BackfillSingularityTrackingStarter(
  private val playlistRepositoryPort: PlaylistRepositoryPort,
  private val spotifyPlaylistPort: SpotifyPlaylistPort,
  private val spotifyAccessTokenPort: SpotifyAccessTokenPort,
  private val appArtistRepositoryPort: AppArtistRepositoryPort,
) : Starter {

  override val id = "BackfillSingularityTrackingStarter-v1"

  override fun execute() {
    val singularityPlaylist = playlistRepositoryPort.findAll().firstOrNull { it.type == PlaylistType.SINGULARITY }
    if (singularityPlaylist == null) {
      logger.info { "No playlist of type ${PlaylistType.SINGULARITY} found, skipping singularity tracking backfill" }
      return
    }

    val playlistDescription = "'${singularityPlaylist.name}' (${singularityPlaylist.spotifyPlaylistId})"
    val accessToken = spotifyAccessTokenPort.getValidAccessToken()
    spotifyPlaylistPort.getPlaylistTrackAddedAtByArtist(accessToken, singularityPlaylist.spotifyPlaylistId).fold(
      { error -> logger.error { "Singularity tracking backfill failed for playlist $playlistDescription: ${error.code}" } },
      { addedAtByArtist ->
        addedAtByArtist.forEach { (artistId, addedAt) -> appArtistRepositoryPort.initializeSingularityTracking(artistId, addedAt) }
        logger.info { "Backfilled singularity tracking for ${addedAtByArtist.size} artist(s) from playlist $playlistDescription" }
      },
    )
  }

  companion object : KLogging()
}
