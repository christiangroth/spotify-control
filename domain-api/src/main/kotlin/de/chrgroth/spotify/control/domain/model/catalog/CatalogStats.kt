package de.chrgroth.spotify.control.domain.model.catalog

data class CatalogStats(
  val artistCount: Long,
  val albumCount: Long,
  val trackCount: Long,
  val undecidedArtistCount: Long = 0L,
  val shallowArtistCount: Long = 0L,
)
