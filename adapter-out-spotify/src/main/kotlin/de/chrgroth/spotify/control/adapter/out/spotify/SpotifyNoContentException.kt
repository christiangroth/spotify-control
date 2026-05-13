package de.chrgroth.spotify.control.adapter.out.spotify

internal class SpotifyNoContentException :
  RuntimeException("Spotify API returned 204 No Content")
