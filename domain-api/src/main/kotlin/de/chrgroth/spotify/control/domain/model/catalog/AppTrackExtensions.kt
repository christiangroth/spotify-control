package de.chrgroth.spotify.control.domain.model.catalog

fun AppTrack.displayArtistName(fallbackResolver: (ArtistId) -> String?): String? {
  val names = listOfNotNull(artistName) + (additionalArtistNames ?: emptyList())
  if (names.isNotEmpty()) {
    return names.distinct().joinToString(", ")
  }
  return fallbackResolver(artistId)
}
