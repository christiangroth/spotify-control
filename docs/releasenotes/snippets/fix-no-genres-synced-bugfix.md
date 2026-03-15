* fix-no-genres-synced: Fixed genre syncing for playlists — the Spotify playlist tracks response was not deserialized correctly, causing all track and artist data to be silently skipped.
* fix-no-genres-synced: Genre data is now also correctly re-fetched when using the resync artist or resync catalog features.
