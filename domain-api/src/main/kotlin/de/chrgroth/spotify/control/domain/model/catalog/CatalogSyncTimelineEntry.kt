package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

enum class CatalogSyncEntityType { ARTIST, ALBUM }

/**
 * A single row in the Catalog Sync timeline (Tools > Catalog Sync), representing either an artist or an album
 * that was recently synced from Spotify. Artist and album fields are mutually exclusive based on [entityType].
 */
data class CatalogSyncTimelineEntry(
  val entityType: CatalogSyncEntityType,
  val syncedAt: Instant,
  val artistId: String?,
  val artistName: String?,
  val albumCount: Int? = null,
  val albumId: String? = null,
  val albumName: String? = null,
  val trackCount: Int? = null,
)
