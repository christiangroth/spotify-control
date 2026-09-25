package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// One-time backfill for #938: initializes AppArtist.singularityIncluded for the whole catalog based on current
// "End of the Road" playlist content - true for artists with a track currently on the playlist, false for every
// other artist. Deliberately no pre-filtering (e.g. via playback history), so the initial state can be fully
// reviewed (see #623).
@ApplicationScoped
@Suppress("Unused")
class BackfillSingularityIncludedStarter(
  private val playlistRepositoryPort: PlaylistRepositoryPort,
  private val appArtistRepositoryPort: AppArtistRepositoryPort,
) : Starter {

  override val id = "BackfillSingularityIncludedStarter-v1"

  override fun execute() {
    val singularityPlaylistInfo = playlistRepositoryPort.findAll().firstOrNull { it.type == PlaylistType.SINGULARITY }
    if (singularityPlaylistInfo == null) {
      logger.info { "No playlist of type ${PlaylistType.SINGULARITY} found, skipping singularityIncluded backfill" }
      return
    }

    val singularityPlaylist = playlistRepositoryPort.findByPlaylistId(singularityPlaylistInfo.spotifyPlaylistId)
    val includedArtistIds = singularityPlaylist?.tracks?.map { it.mainArtistId }?.toSet() ?: emptySet()
    appArtistRepositoryPort.initializeSingularityIncluded(includedArtistIds)
    logger.info {
      "Backfilled singularityIncluded: ${includedArtistIds.size} artist(s) included based on '${singularityPlaylistInfo.name}' (${singularityPlaylistInfo.spotifyPlaylistId})"
    }
  }

  companion object : KLogging()
}
