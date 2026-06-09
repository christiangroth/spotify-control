* Merged catalog outbox partitions `to-spotify-catalog-artist` and `to-spotify-catalog-album` back into a single `to-spotify-catalog` partition to reduce complexity and rate limiting risk.
* Paused the nightly artist catalog sync job.
* Added a one-time starter to enqueue artists found in playback data for sync.
* Added a cleanup starter to remove the old catalog artist and album outbox partition data from the database.
