package de.chrgroth.spotify.control.domain.port.`in`.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelinePage
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceDisplay
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem

interface CatalogBrowserPort {
  fun getCatalogStats(): CatalogStats
  fun getArtists(filter: String?): List<ArtistBrowseItem>
  fun getAlbums(filter: String?): List<AlbumBrowseItem>
  fun getArtistAlbums(artistId: String): List<AlbumBrowseItem>
  fun getAlbumTracks(albumId: String): List<TrackBrowseItem>
  fun getArtistSyncTrace(artistId: String): SyncTraceDisplay?
  fun getAlbumSyncTrace(albumId: String): SyncTraceDisplay?
  fun getSyncTimeline(artistOffset: Int, albumOffset: Int, limit: Int): CatalogSyncTimelinePage
}
