* sync-album-data-on-track-sync: Track enrichment now populates additional track fields (disc number, duration, track number, type) and embeds artist names directly in the track document.
* sync-album-data-on-track-sync: Album data is now synced as part of track enrichment, eliminating the separate album enrichment step.
* sync-album-data-on-track-sync: Album documents now include release date, album type, total tracks, and embedded artist information from the Spotify track API response.
* sync-album-data-on-track-sync: All artists on a track are now queued for enrichment when track details are synced.
