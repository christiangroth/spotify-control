package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId

interface AppAlbumRepositoryPort {
  fun upsertAll(items: List<AppAlbum>)
  fun countAll(): Long
  fun findAll(): List<AppAlbum>
  fun searchByTitle(filter: String, limit: Int): List<AppAlbum>
  fun findByAlbumIds(albumIds: Set<AlbumId>): List<AppAlbum>
  fun findByArtistId(artistId: ArtistId): List<AppAlbum>
  fun findByArtistIds(artistIds: Set<ArtistId>): List<AppAlbum>
  fun findRecentlySynced(offset: Int, limit: Int): List<AppAlbum>
  fun deleteAll()
}
