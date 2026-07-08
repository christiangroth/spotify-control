package de.chrgroth.spotify.control.domain.model.catalog

/**
 * A page of the Catalog Sync timeline. [nextArtistOffset] and [nextAlbumOffset] must be passed back unchanged
 * to fetch the next page, since artists and albums are paged independently before being merged by [CatalogSyncTimelineEntry.syncedAt].
 */
data class CatalogSyncTimelinePage(
  val entries: List<CatalogSyncTimelineEntry>,
  val nextArtistOffset: Int,
  val nextAlbumOffset: Int,
  val hasMore: Boolean,
)
