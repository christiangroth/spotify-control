package de.chrgroth.spotify.control.domain.model.catalog

/**
 * Result of a single page from the Spotify artist albums API.
 */
data class ArtistAlbumsPage(
  val albumIds: List<String>,
  val nextUrl: String?,
)
