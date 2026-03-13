* implement-bulk-fetch: Catalog sync now uses bulk Spotify API endpoints to fetch up to 50 artists or tracks per request, reducing rate limiting during initial sync or large playlist ingestion.
* implement-bulk-fetch: Sync is now scheduled every 10 minutes in staggered batches rather than triggered per item.
