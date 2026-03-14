package de.chrgroth.spotify.control.domain.model

data class CatalogStats(
    val artistCount: Long,
    val albumCount: Long,
    val trackCount: Long,
    val genreCount: Long,
)
