* fix-sync-tracks-issue: Spotify HTTP errors (e.g. 403 Forbidden) on an album or artist lookup are now logged with the full error payload.
* fix-sync-tracks-issue: Sync pool items not processed due to errors are now reset to pending so they are retried on the next sync run instead of being stuck.
