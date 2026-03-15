* remove-bulk-fetch-endpoints: Removed bulk fetch endpoints for artists and tracks (both returned 403).
* remove-bulk-fetch-endpoints: Removed the sync pool collection and all related scheduling jobs.
* remove-bulk-fetch-endpoints: Artist and track sync now enqueues per-item outbox events directly.
