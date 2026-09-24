package de.chrgroth.spotify.control.domain.model.playlist

enum class PlaylistType {
  ALL,
  YEAR,
  SINGULARITY,
  SINGULARITY_STAGING,
  UNKNOWN,
  ;

  companion object {
    const val SINGULARITY_PLAYLIST_NAME = "End of the Road"
    const val SINGULARITY_STAGING_PLAYLIST_NAME = "Start of the Road"
  }
}
