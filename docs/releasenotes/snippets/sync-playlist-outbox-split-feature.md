* Playlist sync now processes one page of tracks at a time, enqueuing catalog sync events per page.
* If a playlist has multiple pages, each page is dispatched as a separate outbox event, preventing long-running tasks and allowing rate limiting to recover gracefully without restarting the full playlist sync.
