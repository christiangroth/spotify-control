* remove-cronjobs-improve-outbox: Catalog enrichment (artist, track, album sync) is now event-driven: sync tasks are enqueued immediately when new items are discovered, instead of waiting for a periodic scheduled job.
* remove-cronjobs-improve-outbox: Removed the three periodic sync-missing scheduler jobs (SyncMissingArtists, SyncMissingTracks, SyncMissingAlbums).
