package de.chrgroth.spotify.control.adapter.out.spotify

internal open class SpotifyApiException(val statusCode: Int, val body: String) :
  RuntimeException("Spotify API error: $statusCode")
