package de.chrgroth.spotify.control.domain.model.catalog

/**
 * Result of a single page from the Spotify artist albums API.
 * Albums carry their full metadata as returned by the discography endpoint, so callers
 * do not need a separate request to fetch metadata for newly discovered albums.
 */
data class ArtistAlbumsPage(
  val albums: List<AppAlbum>,
  val nextUrl: String?,
)
