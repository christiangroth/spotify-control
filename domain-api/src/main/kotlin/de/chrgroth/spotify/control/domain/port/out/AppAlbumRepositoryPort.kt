package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum

interface AppAlbumRepositoryPort {
    fun upsertAll(items: List<AppAlbum>)
    fun findByAlbumIds(albumIds: Set<AlbumId>): List<AppAlbum>
}
