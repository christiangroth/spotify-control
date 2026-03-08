* move-throttling-to-http-handling: Spotify API request throttling is now applied at the HTTP level (2s per request for enrichment and sync calls) instead of at the outbox processing level.
* move-throttling-to-http-handling: Currently playing polling now adapts dynamically: every 10s when playback is active, slowing down to every 90s when no playback is detected.
