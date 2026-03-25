package de.chrgroth.spotify.control.domain.model

import de.chrgroth.spotify.control.domain.util.formatted

data class CatalogStats(
    val artistCount: Long,
    val albumCount: Long,
    val trackCount: Long,
) {
    val artistCountFormatted: String get() = artistCount.formatted()
    val albumCountFormatted: String get() = albumCount.formatted()
    val trackCountFormatted: String get() = trackCount.formatted()
}
