package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.CatalogStats
import de.chrgroth.spotify.control.domain.model.TrackBrowseItem

interface CatalogBrowserPort {
    fun getCatalogStats(): CatalogStats
    fun getArtists(filter: String?): List<ArtistBrowseItem>
    fun getArtistAlbums(artistId: String): List<AlbumBrowseItem>
    fun getAlbumTracks(albumId: String): List<TrackBrowseItem>
}
