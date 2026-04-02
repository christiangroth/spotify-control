package de.chrgroth.spotify.control.domain.catalog

data class CatalogSyncRequest(
  val trackId: String,
  val albumId: String?,
  val artistIds: List<String>,
)
