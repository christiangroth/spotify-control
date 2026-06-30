package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.SyncCause

data class CatalogSyncRequest(
  val trackId: String,
  val artistIds: List<String>,
  val cause: SyncCause,
)
