package de.chrgroth.spotify.control.domain.model.playlist

data class ArtistStats(
  val total: Int,
  val missingFromCatalog: Int,
)
