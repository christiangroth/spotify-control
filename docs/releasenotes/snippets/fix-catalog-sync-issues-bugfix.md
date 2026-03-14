* fix-catalog-sync-issues: Fixed genre information not appearing in catalog view (genres now properly saved on bulk artist sync).
* fix-catalog-sync-issues: Fixed bulk sync fallback: when bulk Spotify endpoint is disabled, existing bulk outbox events are now converted to per-item sync events automatically.
* fix-catalog-sync-issues: Artists and tracks from playback are now only added to the sync pool if they have not been fully synced yet, reducing redundant API calls.
* fix-catalog-sync-issues: Playlist sync now forces re-sync of all artists and tracks regardless of their current sync state.
