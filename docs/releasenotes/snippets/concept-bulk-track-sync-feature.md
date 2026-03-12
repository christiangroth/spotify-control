* concept-bulk-track-sync: Track metadata is now fetched in batches of up to 50 tracks per Spotify API request, reducing rate limit pressure during catalog enrichment.
* concept-bulk-track-sync: Automatically falls back to individual track requests if the bulk endpoint becomes unavailable.
