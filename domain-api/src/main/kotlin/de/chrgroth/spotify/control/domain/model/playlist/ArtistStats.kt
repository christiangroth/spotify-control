package de.chrgroth.spotify.control.domain.model.playlist

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId

data class ArtistStats(
  val total: Int,
  val missingFromCatalog: Int,
  val missingArtistIds: List<ArtistId>,
)
