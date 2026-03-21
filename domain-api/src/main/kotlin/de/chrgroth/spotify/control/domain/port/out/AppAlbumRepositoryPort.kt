package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.ArtistId

interface AppAlbumRepositoryPort {
    fun upsertAll(items: List<AppAlbum>)
    fun countAll(): Long
    fun findAll(): List<AppAlbum>
    fun findByAlbumIds(albumIds: Set<AlbumId>): List<AppAlbum>
    fun findByArtistId(artistId: ArtistId): List<AppAlbum>
    fun deleteAll()
}
