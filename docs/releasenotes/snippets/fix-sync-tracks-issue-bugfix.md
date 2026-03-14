* fix-sync-tracks-issue: Track sync no longer fails for the entire batch when a single album lookup returns a 403 Forbidden response from Spotify.
* fix-sync-tracks-issue: Tracks whose album fetch fails with a non-rate-limit error now fall back to direct track sync instead of aborting the whole task.
