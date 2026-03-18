package de.chrgroth.spotify.control.domain.model

data class AlbumBrowseItem(
    val albumId: String,
    val title: String?,
    val imageLink: String?,
    val releaseDate: String?,
    val trackCount: Int,
    val durationFormatted: String,
) {
    val trackCountFormatted: String get() = trackCount.formatted()
}
