package de.chrgroth.spotify.control.adapter.out.spotify

internal class SpotifyRateLimitException(val retryAfterSeconds: Long) :
  SpotifyApiException(HTTP_RATE_LIMITED, "Rate limit exceeded, retry after ${retryAfterSeconds}s")
