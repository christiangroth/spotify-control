package de.chrgroth.spotify.control.domain.model

data class ArtistBrowseItem(
    val artistId: String,
    val artistName: String,
    val imageLink: String?,
    val albumCount: Int,
    val trackCount: Int,
)
