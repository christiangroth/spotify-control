package de.chrgroth.spotify.control.domain.model

import de.chrgroth.spotify.control.domain.util.formatted

data class ArtistBrowseItem(
    val artistId: String,
    val artistName: String,
    val imageLink: String?,
    val albumCount: Int,
    val trackCount: Int,
) {
    val albumCountFormatted: String get() = albumCount.formatted()
    val trackCountFormatted: String get() = trackCount.formatted()
}
