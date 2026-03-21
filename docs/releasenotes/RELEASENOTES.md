# 0.73.8 (2026.03.21)

## Bugfixes / Chore
* Removed MongoDB query timeout and fallback value mechanism.



---

# 0.73.7 (2026.03.21)

## Bugfixes / Chore
* Dashboard stats sub-queries now run in parallel, reducing overall load time.



---

# 0.73.6 (2026.03.21)

## Bugfixes / Chore
* Fixed dashboard stats timeouts by splitting queries into focused per-section operations.
* Each dashboard section now only runs the queries it needs instead of loading all stats at once.
* Added MongoDB compound index on playback data to speed up listening stats aggregation.



---

# 0.73.5 (2026.03.21)

## Bugfixes / Chore
* Fixed dashboard stats timeout by replacing full document fetch with a MongoDB aggregation for listening stats.



---

# 0.73.4 (2026.03.21)

## Bugfixes / Chore
* Internal refactoring: consolidated catalog sync logic into a shared component.



---

# 0.73.3 (2026.03.21)

## Bugfixes / Chore
* Fixed dashboard stats query timeout caused by inefficient MongoDB lookups.



---

# 0.73.2 (2026.03.21)

## Bugfixes / Chore
* MongoDB queries now time out after 2 seconds and return safe default values when MongoDB Atlas is unavailable or slow.
* Timeout counts per query are now tracked and visible in the health dashboard.



---

# 0.73.1 (2026.03.19)

## Bugfixes / Chore
* Updated quarkus starters dependency to 0.6.1.



---

# 0.73.0 (2026.03.18)

## New Features
* MongoDB Viewer: new page at /mongodb-viewer to browse MongoDB collections with field-level filters (string contains, ID equals/in/not in), sorting, and paginated results (10/25/50/100 per page).
* Renamed MongoDB dropdown item to Atlas.



---

# 0.72.1 (2026.03.18)

## Bugfixes / Chore
* Track duration is now shown for recently played tracks on the dashboard.



---

# 0.72.0 (2026.03.18)

## New Features
* Slack notifications are sent when a playlist check changes from failed to passed.
* Slack notifications are sent when a playlist check stays failed but its violations change.



---

# 0.71.0 (2026.03.18)

## New Features
* Added "Sync from Playback" button to the catalog page to enqueue album syncs for tracks found in playback history but missing from the catalog.



---

# 0.70.1 (2026.03.18)

## Bugfixes / Chore
* Restored default Spotify request throttle interval to 10s.



---

# 0.70.0 (2026.03.18)

## New Features
* Catalog Re-sync and Wipe Catalog actions are now displayed side by side.
* Added "Catalog Browser" heading above the catalog filter and table.
* Added "Actions" column header to the catalog artist table.
* Album duration is now displayed as HH:mm:ss.
* Album rows now show a hover effect, matching the artist row behaviour.
* Removed stale "No albums found." and "No tracks found." messages from the catalog page.
* Playlists are now ordered alphabetically.
* Removed playlist type badge and type-change prompt from the playlists page.
* Playlist checks and playlists within each check group are now ordered alphabetically.
* Playlist check rows now show an ok/not-ok icon; the green "none" label is replaced by "-".



---

# 0.69.0 (2026.03.18)

## New Features
* Dashboard catalog stats are now displayed in a single combined widget showing Artists, Albums, and Tracks.
* Dashboard widgets are reordered: Playlists synced and Playlist checks are in the first row alongside the catalog widget, with Playback Events in the second row.
* All numeric values on the dashboard now use dot-separated thousands formatting (e.g. 1.234.567).



---

# 0.68.5 (2026.03.18)

## Bugfixes / Chore
* Menu bar health widgets now refresh correctly when the SSE connection reconnects after a drop.
* The outbox status popup now shows a live countdown for paused partitions.
* The resume button in the outbox status popup now works on all pages.



---

# 0.68.3 (2026.03.18)

## Bugfixes / Chore
* Fixed Slack notifications not working due to missing beans.xml in the notification adapter module.



---

# 0.68.1 (2026.03.16)

## Bugfixes / Chore
* Reduced default Spotify request throttle interval from 10s to 8s.



---

# 0.68.0 (2026.03.16)

## New Features
* Added outbox status indicator in the navbar: green when all outbox partitions are active, red when any are paused or rate-limited. Hover to view full outbox partition details.
* Added playback status indicator in the navbar: green when playback is active, grey when no playback is detected.
* Both health indicators are hidden on the login page and kept up to date via SSE on all other pages.
* On the health page, state indicators and cronjobs are now shown side by side in one row.
* State indicators now show a "Since" column with the last check timestamp, use a grey icon for inactive state, and display the status icon before the predicate name.



---

# 0.67.2 (2026.03.16)

## Bugfixes / Chore
* Updated outbox-starters to 0.6.0.



---

# 0.67.1 (2026.03.16)

## Bugfixes / Chore
* Updated quarkus-one-time-starters dependency to version 0.5.0.



---

# 0.67.0 (2026.03.15)

## New Features
* Added Slack notifications framework with system notification support.
* System notifications for application startup, stopping, and outbox partition pause/resume events.
* Notifications are individually configurable and enabled by default; webhook URL is set via `SLACK_WEBHOOK_URL` environment variable.



---

# 0.66.2 (2026.03.15)

## Bugfixes / Chore
* Runtime config settings (Spotify request throttling) are now shown as the first section on the Config page instead of a separate page.



---

# 0.66.1 (2026.03.15)

## Bugfixes / Chore
* Optimized user login to use a single database operation instead of two, reducing load on the database.



---

# 0.66.0 (2026.03.15)

## New Features
* Dashboard now updates Artists, Albums, and Tracks counts in real-time via SSE when catalog data is synced.



---

# 0.65.0 (2026.03.15)

## New Features
* New Runtime Config settings page under `/settings/runtime-config`.
* Spotify HTTP request throttle interval is now editable at runtime (transient, resets on restart).



---

# 0.64.2 (2026.03.15)

## Bugfixes / Chore
* Removed genre information from artist catalog, dashboard stats, and listening stats since Spotify no longer provides genre data.



---

# 0.64.1 (2026.03.15)

## Bugfixes / Chore
* Albums are now fully re-synced after clearing catalog data, fixing incomplete track lists.



---

# 0.64.0 (2026.03.15)

## New Features
* Added Wipe Catalog button to catalog UI.
* Wipe operation deletes all catalog data (artists, albums, tracks), removes catalog sync outbox events, sets all playlists to sync inactive and deletes all playlist checks.
* Wipe requires typing "yes" in a confirmation popup to prevent accidental data loss.

## Bugfixes / Chore
* Dashboard listening stats now only reflect app-tracked playback durations, excluding tracks where no listening time was recorded by the app.



---

# 0.63.4 (2026.03.15)

## Bugfixes / Chore
* Improved query performance to reduce slow database operations.
* Artist and album sync tasks are no longer enqueued when the artist or album is already present in the catalog.



---

# 0.63.3 (2026.03.15)

## Bugfixes / Chore
* Removed bulk fetch endpoints for artists and tracks (both returned 403).
* Removed the sync pool collection and all related scheduling jobs.
* Artist and track sync now enqueues per-item outbox events directly.



---

# 0.63.2 (2026.03.15)

## Bugfixes / Chore
* Playlist last sync time is now updated in the UI after manually syncing a playlist.



---

# 0.63.1 (2026.03.14)

## Bugfixes / Chore
* Activating a playlist now always triggers a data sync, not just playlist checks.



---

# 0.63.0 (2026.03.14)

## New Features
* Added startup task to delete all catalog data (tracks, albums, artists, playlist checks) for a clean resync.



---

# 0.62.0 (2026.03.14)

## New Features
* Deletes playlist check documents when a playlist sync status is set to inactive.
* Enqueues playlist checks when a playlist sync status is set to active (if playlist data already exists).
* Adds a WipePlaylistChecksStarter to wipe all playlist check documents on demand.



---

# 0.61.3 (2026.03.14)

## Bugfixes / Chore
* Spotify HTTP error responses now include the request path in log messages for easier diagnosis.
* Error responses from playlist and playback settings endpoints are now logged on the server side.



---

# 0.61.2 (2026.03.14)

## Bugfixes / Chore
* Moved the Re-sync Catalog button to the top of the Catalog page.
* Removed the Re-sync Catalog section from the Playback Settings page.



---

# 0.61.1 (2026.03.14)

## Bugfixes / Chore
* Tracks, artists and albums are now only stored after a full Spotify API sync, never as partial stubs.
* Playlist and playback sync now only schedule IDs for sync instead of immediately writing incomplete catalog data.



---

# 0.61.0 (2026.03.14)

## New Features
* Removed Status column from Playlist Checks table.
* Violations column now shows "none" in green when no violations are present.
* Renamed "Check Date" column header to "Checked".
* Added check name sub heading above each check type table.



---

# 0.60.0 (2026.03.14)

## New Features
* Moved catalog re-sync action to the top of the Playback Settings page.
* Removed per-artist re-sync search from Playback Settings page.
* Added Re-sync button per artist in the Catalog page.
* Playlist checks are now automatically enqueued on startup for all active synced playlists.
* Added timer metrics for playlist checks tagged with check id and playlist id.

## Bugfixes / Chore
* Fixed genre information not appearing in catalog view (genres now properly saved on bulk artist sync).
* Fixed bulk sync fallback: when bulk Spotify endpoint is disabled, existing bulk outbox events are now converted to per-item sync events automatically.
* Artists and tracks from playback are now only added to the sync pool if they have not been fully synced yet, reducing redundant API calls.
* Playlist sync now forces re-sync of all artists and tracks regardless of their current sync state.



---

# 0.59.4 (2026.03.14)

## Bugfixes / Chore
* Dashboard recently played now shows album name and cover art.



---

# 0.59.3 (2026.03.14)

## Bugfixes / Chore
* Spotify HTTP errors (e.g. 403 Forbidden) on an album or artist lookup are now logged with the full error payload.
* Sync pool items not processed due to errors are now reset to pending so they are retried on the next sync run instead of being stuck.



---

# 0.59.2 (2026.03.14)

## Bugfixes / Chore
* Recently Played entries now show album artwork and album name from the catalog.
* Listening Stats now use catalog track duration for recently-played items so minutes listened, top tracks, artists, and genres are no longer zero.



---

# 0.59.1 (2026.03.14)

## Bugfixes / Chore
* Fixed catalog page rendering error caused by missing album and track data in the template.



---

# 0.59.0 (2026.03.14)

## New Features
* Synced playlists now have a type (ALL, YEAR, UNKNOWN).
* Type is automatically assigned when activating a playlist for sync (4-digit name → YEAR, otherwise → UNKNOWN).
* Type ALL must be set manually and only one playlist may have it.
* Playlist type is displayed as a tag in the playlist settings UI and can be changed manually.



---

# 0.58.0 (2026.03.14)

## New Features
* Added playlist checks framework with duplicate track detection.
* New dashboard widget shows succeeded/total playlist checks count (green/red).
* New playlist checks page lists all check results with violations.



---

# 0.57.0 (2026.03.14)

## New Features
* New catalog browser UI accessible from the dashboard.
* Dashboard now shows catalog stats (artists, albums, tracks, genres) with links to the catalog browser.
* Catalog browser shows all artists sorted alphabetically with image, genres, album and track counts.
* Clicking an artist in the catalog reveals their albums sorted by release date with image, track count and duration.
* Clicking an album in the catalog expands the track list sorted by track number with duration.
* Removed Playlists link from navigation menu.
* Clicking "Playlists synced" on the dashboard now links to playlist settings.



---

# 0.56.0 (2026.03.14)

## New Features
* Artist and track sync now falls back to single-item fetching when the Spotify bulk endpoints are no longer available.
* Health page now shows a new "State" section with the current status of the sync pool and playback activity predicates.



---

# 0.55.1 (2026.03.14)

## Bugfixes / Chore
* Config page now shows environment variables first (full width), followed by config properties (full width).
* Masking config keys are no longer shown in the config table.
* spotify.client-id, APP_ALLOWED_SPOTIFY_USER_IDS and SPOTIFY_CLIENT_ID are no longer masked.



---

# 0.55.0 (2026.03.14)

## New Features
* Sync pool tasks now include the specific IDs to sync, making all pending tasks visible in the outbox.
* Sync scheduler jobs run every 3 hours instead of every 10 minutes.



---

# 0.54.0 (2026.03.14)

## New Features
* Config page added with Config and Environment sections accessible from the technical menu.
* Sensitive configuration and environment values are now masked (spotify.client-id, quarkus.mongodb.connection-string, mongodb.connection.string, SPOTIFY_CLIENT_ID, HTTP_AUTH_ENCRYPTION_KEY, APP_ALLOWED_SPOTIFY_USER_IDS).



---

# 0.53.0 (2026.03.14)

## New Features
* Added Configuration section to the Health UI page.
* Shows all configuration values and environment variables in tables.
* Sensitive config and environment keys are masked with configurable key lists.



---

# 0.52.0 (2026.03.14)

## New Features
* Track sync now fetches all tracks for an album in a single request when the album is known, reducing the number of Spotify API calls.
* All tracks in an album are stored when syncing, even if only some were originally requested.



---

# 0.51.0 (2026.03.13)

## New Features
* Added catalog re-sync functionality to refresh artist and track metadata from Spotify.
* Catalog is automatically re-synced every week via a scheduled job.
* Added "Re-sync Catalog" button in Playback Settings to trigger a manual catalog re-sync.



---

# 0.50.1 (2026.03.13)

## Bugfixes / Chore
* Fixed release notes not being up to date in the Docker container by ensuring the release notes are fully generated and written before the Quarkus application is rebuilt during release.



---

# 0.50.0 (2026.03.13)

## New Features
* Catalog sync now uses bulk Spotify API endpoints to fetch up to 50 artists or tracks per request, reducing rate limiting during initial sync or large playlist ingestion.
* Sync is now scheduled every 10 minutes in staggered batches rather than triggered per item.



---

# 0.49.0 (2026.03.13)

## New Features
* Added "Update from playlists" button on the playback settings page to automatically sync artist playback processing states based on their presence in active playlists.



---

# 0.48.4 (2026.03.13)

## Bugfixes / Chore
* Added link to Spotify Web API documentation in the Technical dropdown menu.



---

# 0.48.3 (2026.03.13)

## Bugfixes / Chore
* Artist names on /settings/playback are now readable with white text on the dark background.



---

# 0.48.2 (2026.03.13)

## Bugfixes / Chore
* Added Copilot environment setup to enable building with authenticated GitHub Package Registry dependencies.
* Migrates Track title, Album title, and Artist genre fields to updated entity structures.
* Remove Jitpack.io repository as all dependencies are available on Maven Central.
* Replaced Jackson dependency with kotlinx-serialization for Spotify API response handling.



---

# 0.48.1 (2026.03.13)

## Bugfixes / Chore
* Cleaned up obsolete `recently_partial_played` MongoDB collection.



---

# 0.48.0 (2026.03.13)

## New Features
* Track enrichment now populates additional track fields (disc number, duration, track number, type) and embeds artist names directly in the track document.
* Album data is now synced as part of track enrichment, eliminating the separate album enrichment step.
* Album documents now include release date, album type, total tracks, and embedded artist information from the Spotify track API response.
* All artists on a track are now queued for enrichment when track details are synced.



---

# 0.47.0 (2026.03.12)

## New Features
* Artist information is now more detailed with genre classification and artist type synced from Spotify.



---

# 0.46.5 (2026.03.12)

## Bugfixes / Chore
* Renamed `SpotifyAccessTokenService` to `SpotifyAccessTokenAdapter` for consistent hexagonal naming conventions.
* Fixed test package names for web adapter tests from `adapter.web.in` to `adapter.in.web`.
* Rewrote arc42 documentation: removed all unimplemented feature references, added PlantUML diagrams via kroki, sorted modules alphabetically, restructured external dependencies, filled out Risks and Technical Debts.
* Added ADR 0007 documenting the Persistent Outbox Pattern decision.
* Updated coding guidelines: corrected outbox partition names, aligned test strategy description with actual implementation.



---

# 0.46.4 (2026.03.12)

## Bugfixes / Chore
* Outbox event details on health page are now expanded by default and can no longer be collapsed.
* Reduced fade animation duration on SSE update events.



---

# 0.46.3 (2026.03.12)

## Bugfixes / Chore
* Health UI is now updated via SSE when the number of outbox tasks changes.
* Outgoing Spotify API requests are now grouped by endpoint type in request stats and metrics (e.g. `/v1/tracks/{id}` instead of individual track IDs).



---

# 0.46.2 (2026.03.12)

## Bugfixes / Chore
* Increased Spotify catalog API throttle interval from 5s to 10s to reduce rate limiting during bulk catalog enrichment.


---

# 0.46.1 (2026.03.12)

## Bugfixes / Chore
* Removed `/ui` prefix from all web paths and package names.
* Renamed `DashboardSseService` and `HealthSseService` to `DashboardSseAdapter` and `HealthSseAdapter`.
* Extracted common SSE connect logic into `connectSse()` helper in `sse-utils.js`.
* Extracted user placeholder SVG into reusable layout symbol `#icon-user-placeholder`.
* Removed duplicate first heading from docs pages (heading is now shown as page title only).
* Renamed MongoDB `CurrentlyPlayingDocument`, `RecentlyPlayedDocument`, and `RecentlyPartialPlayedDocument` to use `Spotify` prefix; renamed collection `recently_partial_played` to `spotify_recently_partial_played`.
* Replaced dynamic timer in `CurrentlyPlayingFetchJob` with a custom `CurrentlyPlayingSkipPredicate`.
* Moved `SchedulerInfoAdapter` and `CurrentlyPlayingScheduleState` to new `adapter-out-scheduler` module.



---

# 0.46.0 (2026.03.10)

## New Features
* Consolidated Spotify playback ports into a single SpotifyPlaybackPort interface.
* Consolidated Spotify catalog ports into a single SpotifyCatalogPort interface.
* Merged playlist tracks into SpotifyPlaylistPort.
* Merged outbox management ports into a single OutboxManagementPort interface.
* Merged playlist repository ports into a single PlaylistRepositoryPort interface.

## Bugfixes / Chore
* Introduced PlaylistId, ArtistId, AlbumId, and TrackId value classes.
* Combined related port interfaces (PlaybackPort, PlaylistPort, HealthPort, DashboardPort, UserProfilePort, LoginServicePort).



---

# 0.45.0 (2026.03.09)

## New Features
* Removed sample API endpoint (`GET /api/hello`) and all related code.



---

# 0.44.3 (2026.03.09)

## Bugfixes / Chore
* Configuration properties are now declared in each owning module's application.properties instead of being centralised in application-quarkus.



---

# 0.44.2 (2026.03.09)

## Bugfixes / Chore
* Resuming a paused outbox partition via the Health UI now correctly triggers event processing.

---

# 0.44.1 (2026.03.09)

## Bugfixes / Chore
* Fix resume partition button on the health page.
* Fix show events per type on the health page.

---

# 0.44.0 (2026.03.09)

## New Features
* Add button to manually resume paused outbox partitions on the health page.
* Strip hostname from outgoing HTTP request endpoints on the health page.
* Blocked countdown now shows only the countdown (no timestamp) for near-future blocks.
* Renamed various health page column headers for brevity.

---

# 0.43.0 (2026.03.09)

## New Features
* Outgoing HTTP requests on the health page are now grouped by endpoint instead of host.

---

# 0.42.0 (2026.03.09)

## New Features
* Removed unused panels from the metrics dashboard.
* Incoming HTTP requests panel no longer shows redirect responses.
* Heap and non-heap memory "max" series are now hidden by default.
* Spotify API request URLs are now grouped by URL pattern, so requests to the same endpoint with different IDs are aggregated together.
* Failed and rate-limited task rate panels now display 0 instead of showing no data when no failures have occurred.
* Partition status panel now always shows the last known state, even when no data was recorded in the selected time range.
* Task enqueue and task processed rates are now shown in separate panels.

---

# 0.41.1 (2026.03.09)

## Bugfixes / Chore
* Artist names are now correctly displayed in playback settings, including after enrichment data is fetched from Spotify.

---

# 0.41.0 (2026.03.09)

## New Features
* Artist names are now correctly displayed alongside their images in the playback settings list.
* Artists are sorted alphabetically within each status group.
* Added a filter input to the Artist Playback Processing section to search across all three lists by name (regex, case insensitive).
* Each status column now shows the current item count, e.g. Undecided (138), updated live as the filter is applied.

---

# 0.40.3 (2026.03.09)

## Bugfixes / Chore
* Fixed Grafana metrics dashboard queries.

---

# 0.40.2 (2026.03.09)

## Bugfixes / Chore
* Fixed Grafana metrics dashboard scrape configuration.

---

# 0.40.1 (2026.03.09)

## Bugfixes / Chore
* Increased Spotify API request throttle interval to reduce rate limiting.
* Increased inactive playback polling interval to reduce unnecessary requests.

---

# 0.40.0 (2026.03.09)

## New Features
* Extracted outbox functionality as a standalone external library.

---

# 0.39.0 (2026.03.09)

## New Features
* Extracted starters functionality as a standalone external library.

---

# 0.38.1 (2026.03.09)

## Bugfixes / Chore
* Improved performance of artist and track lookups.

---

# 0.38.0 (2026.03.08)
## New Features
* Spotify API requests are now throttled per request to reduce rate limiting.
* Currently playing polling now adapts dynamically: every 10s when playback is active, slowing down to every 90s when no playback is detected.

---

# 0.37.0 (2026.03.08)
## New Features
* Improved logging when outbox tasks are executed.

---

# 0.36.1 (2026.03.08)
## Bugfixes / Chore
* Reduced Spotify API call volume and improved rate limiting handling.

---

# 0.36.0 (2026.03.08)
## New Features
* Split settings UI into separate playlist settings (/ui/settings/playlist) and playback settings (/ui/settings/playback) pages.

---

# 0.35.2 (2026.03.07)
## Bugfixes / Chore
* Extracted the release-notes plugin as a standalone external dependency.

---

# 0.35.1 (2026.03.07)
## Bugfixes / Chore
* Added missing MongoDB indexes to improve query performance.
* Fixed outbox SSE not updating the health UI when tasks are enqueued.

---

# 0.35.0 (2026.03.07)
## New Features
* Artists can now be ignored for playback processing.
* Setting an artist to inactive removes their playback data; reactivating triggers a rebuild.
* Settings UI shows artists in three columns: undecided, active, and inactive.

---

# 0.34.0 (2026.03.07)
## New Features
* Added listening stats panel to the dashboard showing listened minutes, top 3 tracks, top 3 artists, and top 3 genres for the last 30 days, ranked by listening duration.

---

# 0.33.0 (2026.03.07)
## New Features
* Spotify playback data is now processed into dedicated collections for tracks, artists, and albums.
* Track, artist, and album metadata are now stored in separate collections to avoid duplication.
* Added a Recreate Playback Data button on the settings page to rebuild processed playback data from scratch.
* Dashboard stats (totals, histogram, recently played tracks) now sourced from processed collections.

---

# 0.32.0 (2026.03.07)
## New Features
* Added GitHub repository link (Code) to the technical dropdown menu in the navigation bar.

---

# 0.31.0 (2026.03.07)
## New Features
* Added Grafana metrics dashboard covering JVM, logging, HTTP server/client, outbox, scheduler and starters.
* Added Loki logs dashboard for structured log exploration in Grafana Cloud.
* CI job provisions the metrics dashboard to Grafana Cloud after each release.
* Added Logs and Metrics links with Grafana logo to the technical menu in the navigation bar.

---

# 0.30.2 (2026.03.07)
## Bugfixes / Chore
* Added missing OAuth scope so the currently playing endpoint can be accessed.

---

# 0.30.1 (2026.03.07)
## Bugfixes / Chore
* Fixed an issue where tasks in a partition were not executed after being rate-limited.

---

# 0.29.0 (2026.03.06)
## New Features
* Outgoing Spotify API requests are now rate-limited to avoid hitting Spotify rate limits.

---

# 0.28.1 (2026.03.06)
## Bugfixes / Chore
* Fixed playlist track mapping.

---

# 0.28.0 (2026.03.06)
## New Features
* Capture partial listens and skipped tracks to improve listening statistics.

---

# 0.27.1 (2026.03.06)
## Bugfixes / Chore
* Fixed sync playlist button to only show for active playlists with green icon styling.

---

# 0.27.0 (2026.03.06)
## New Features
* Streamlined health UI.

---

# 0.26.0 (2026.03.06)
## New Features
* Added a Sync button per playlist on the settings page.

---

# 0.25.0 (2026.03.06)
## New Features
* Added a "Technical" dropdown sub-menu in the navigation bar, grouping Health, Loki, MongoDB, and Docs links together to reduce clutter.

---

# 0.24.2 (2026.03.06)
## Bugfixes / Chore
* Fixed playlist track sync always returning 0 items.

---

# 0.24.1 (2026.03.06)
## Bugfixes / Chore
* Outbox partitions are now always initialized at startup.

---

# 0.24.0 (2026.03.06)
## New Features
* Dashboard now shows recently played tracks in a new panel (configurable limit, default: 13 tracks).

---

# 0.23.1 (2026.03.06)
## Bugfixes / Chore
* Added margin to menu bar icons for improved readability.

---

# 0.23.0 (2026.03.06)
## New Features
* Increased SSE update fade animation duration to 3 seconds for dashboard and health UI.

---

# 0.22.0 (2026.03.06)
## New Features
* Failed outbox tasks that have exhausted all retry attempts are now moved to an archive instead of remaining in the main queue.

---

# 0.21.0 (2026.03.06)
## New Features
* Add link to MongoDB Atlas Data Explorer in navigation menu bar.

---

# 0.20.0 (2026.03.06)
## New Features
* Added a link to Grafana Cloud Logs Dashboard in the menu bar.

---

# 0.19.4 (2026.03.06)
## Bugfixes / Chore
* Fixed playlist data fetch by using the correct Spotify API endpoint.

---

# 0.19.3 (2026.03.06)
## Bugfixes / Chore
* Fixed automatic version bump during release.

---

# 0.19.2 (2026.03.06)
## New Features
* Version bump is now performed automatically before the release build based on snippet types.

---

# 0.19.1 (2026.03.06)
## Bugfixes / Chore
* Fixed cronjob table pulse animation and row resorting after execution.

---

# 0.19.0 (2026.03.06)
## New Features
* Recently-played fetching now only requests data newer than the last known playback timestamp, reducing redundant data transfer.

---

# 0.18.0 (2026.03.06)
## New Features
* Simplified "Blocked Until" display for outbox partitions – shows only time (HH:mm) with live countdown when less than 24h away, full date otherwise.

---

# 0.17.0 (2026.03.06)
## New Features
* MongoDB collection sizes are now displayed in kilobytes (kb) instead of bytes.
* Show blocked-until timestamp in outbox health when a partition is blocked.
* Cronjobs in the health UI are now sorted by remaining time until next execution (ascending).

---

# 0.16.9 (2026.03.06)
## New Features
* Health page cronjob overview now shows all scheduled jobs including paused/disabled ones, and displays a Status column indicating whether each job is active or paused.

---

# 0.16.8 (2026.03.06)
## Bugfixes / Chore
* Fixed 403 errors when fetching tracks of collaborative playlists.

---

# 0.16.7 (2026.03.06)
## New Features
* Added cronjob overview to health UI showing all configured cronjobs with their schedule and a live countdown to the next execution.

---

# 0.16.6 (2026.03.06)
## Bugfixes / Chore
* Fixed relative links between documentation markdown files.

---

# 0.16.5 (2026.03.06)
## Bugfixes / Chore
* Fixed missing SSE event when toggling playlist sync status on settings UI.

---

# 0.16.4 (2026.03.04)
## Bugfixes / Chore
* Fixed login page redirect — users with a valid session are now properly redirected to the dashboard when reloading or revisiting the login page.
* Session cookie is now persistent across browser restarts.

---

# 0.16.0 (2026.03.04)
## New Features
* Added MongoDB collection stats (name, document count, size) and query stats (name, executions in 24h, slow query count) to the health UI, with communication and MongoDB sub-sections.

---

# 0.15.2 (2026.03.04)
## Bugfixes / Chore
* Podcast episodes and other non-track items are now removed from recently played history.

---

# 0.15.1 (2026.03.04)
## Bugfixes / Chore
* Playlist metadata for playlists not owned by the user is now removed.

---

# 0.14.0 (2026.03.04)
## New Features
* Added dedicated health monitoring page at /ui/health with system health stats and real-time SSE updates.

---

# 0.13.0 (2026.03.04)
## New Features
* Added playlist entity with full track data (tracks including artist information).
* Playlist data sync is triggered automatically when a playlist's snapshot ID changes.
* Playlist data sync is also triggered when a playlist is marked as active but has no synced data yet.

---

# 0.12.10 (2026.03.04)
## Bugfixes / Chore
* Only store playlist metadata for playlists owned by the user (not followed playlists).

---

# 0.12.9 (2026.03.04)
## Bugfixes / Chore
* Format the date of the last Playlist Metadata sync using German locale on the Playlists settings page.

---

# 0.12.8 (2026.03.04)
## Bugfixes / Chore
* Playlists icon in header is now left-aligned and uses a playlist-style icon.

---

# 0.12.7 (2026.03.04)
## Bugfixes / Chore
* Dashboard updates now use fine-grained SSE events per section with partial page updates and a fade effect instead of full page reloads.

---

# 0.12.6 (2026.03.04)
## Bugfixes / Chore
* Podcast episodes and other non-track playback events are now ignored when fetching recently played history.

---

# 0.12.5 (2026.03.04)
## New Features
* Playlist sync now triggers a Dashboard SSE refresh event when the number of playlists changes.

---

# 0.12.4 (2026.03.04)
## New Features
* Playlist metadata is now stored in a separate collection, preventing user profile syncs from overwriting playlist data.

---

# 0.12.3 (2026.03.04)
## Bugfixes / Chore
* Fixed scheduled playlist sync incorrectly overwriting user-configured sync status.
* Fixed last sync time being reset on every sync regardless of actual changes.

---

# 0.12.1 (2026.03.04)
## New Features
* Date labels under playback events histogram columns are now only shown on large screens.

---

# 0.12.0 (2026.03.04)
## New Features
* Added playlists synced stats to dashboard and reorganized dashboard sections into Spotify data and System Health.

---

# 0.11.7 (2026.03.04)
## Bugfixes / Chore
* SSE dashboard refresh is now also triggered by changes in outgoing HTTP request metrics.

---

# 0.11.6 (2026.03.04)
## Bugfixes / Chore
* New found playlists default to PASSIVE sync status instead of ACTIVE.

---

# 0.11.5 (2026.03.04)
## New Features
* Playlists settings page now shows heading "Playlists" and includes a "Sync Now" button to trigger an immediate sync.

---

# 0.11.4 (2026.03.04)
## New Features
* Add Spotify playlist sync settings with hourly sync job and settings UI to manage per-playlist sync status.

---

# 0.11.3 (2026.03.04)
## New Features
* Add Prometheus metrics for outgoing Spotify API requests.
* Add Spotify request stats panel to dashboard showing outgoing request counts per host (last 24h).

---

# 0.11.2 (2026.03.04)
## Bugfixes / Chore
* Fixed dashboard SSE connection error.

---

# 0.11.0 (2026.03.04)
## New Features
* Add logo to header bar.
* Replace Docs text in menu bar with icon.
* Make Docs link always visible when authenticated.
* Dashboard page now uses SSE for live updates.

---

# 0.10.2 (2026.03.04)
## New Features
* Upgraded to Java 25 (LTS) and Kotlin 2.3.10.

---

# 0.10.1 (2026.03.04)
## New Features
* Redirect authenticated users from the index page to the dashboard UI when a valid session cookie is present.

---

# 0.10.0 (2026.03.04)
## New Features
* Add MongoDB query metrics and slow query detection.

---

# 0.9.4 (2026.03.03)
## Bugfixes / Chore
* Streamlined navigation links across all pages.
* Fixed partition stats table text color.
* Fixed docs page headline styling to match dashboard heading.
* Added Spotify favicon.
* Switched dashboard stats refresh to 60-second polling.

---

# 0.9.3 (2026.03.03)
## Bugfixes / Chore
* Increased polling frequency for recently played tracks from every 15 minutes to every 10 minutes.

---

# 0.9.2 (2026.03.03)
## Bugfixes / Chore
* Fixed docs markdown rendering issue.

---

# 0.9.1 (2026.03.03)
## Bugfixes / Chore
* Fixed partition information table to use dark mode styling on dashboard.

---

# 0.9.0 (2026.03.03)
## New Features
* Dashboard now shows a personalised greeting, playback statistics, outbox partition health, and live updates via server-sent events.

---

# 0.8.0 (2026.03.03)
## New Features
* Added nightly cleanup job for outbox archive that deletes documents older than a configurable number of days (default: 365).

---

# 0.7.7 (2026.03.03)
## New Features
* Updated MongoDB Atlas connection configuration.

---

# 0.7.1 (2026.03.02)
## New Features
* Add container name and service name as log metadata labels for Loki log filtering.

---

# 0.7.0 (2026.03.02)
## New Features
* Add monitoring via Grafana Alloy forwarding Prometheus metrics and structured JSON logs to Grafana Cloud.
* Add custom outbox metrics (enqueued, processed, failed counters per partition).

---

# 0.6.0 (2026.03.01)
## New Features
* Implemented outbox throttling to handle Spotify API rate limits.

---

# 0.5.0 (2026.03.01)
## New Features
* Connect outbox for profile update and recently played fetch.

---

# 0.4.2 (2026.02.28)
## New Features
* Added outbox module with task queue, retry, deduplication, and partition management.

---

# 0.4.0 (2026.02.28)
## New Features
* Fetch and persist recently played tracks per user with duplicate suppression.

---

# 0.3.0 (2026.02.27)
## New Features
* User display names are now refreshed nightly from Spotify via a scheduled job running at 4am.

---

# 0.2.2 (2026.02.27)
## Bugfixes / Chore
* Fix OAuth login callback returning 500 Internal Server Error on unexpected exceptions by catching them and redirecting to the error page.

---

# 0.2.0 (2026.02.27)
## New Features
* Improved error handling.

---

# 0.1.11 (2026.02.27)
## New Features
* Added logging across all relevant classes.

---

# 0.1.10 (2026.02.26)
## New Features
* Spotify access tokens are now automatically renewed before expiry.

---

# 0.1.9 (2026.02.26)
## New Features
* Implement Spotify OAuth 2.0 login with allow-listed users.

---

# 0.1.8 (2026.02.26)
## Bugfixes / Chore
* Fixed build version not being shown in UI in dev mode.

---

# 0.1.7 (2026.02.26)
## New Features
* Added user allow list support via APP_ALLOWED_SPOTIFY_USER_IDS environment variable (comma-separated Spotify user IDs).

---

# 0.1.6 (2026.02.25)
## New Features
* Implement Spotify user persistence.

---

# 0.1.5 (2026.02.25)
## Bugfixes / Chore
* Fixed build version not being shown in the UI in dev mode.

---

# 0.1.3 (2026.02.25)
## New Features
* Added scheduled GitHub Actions workflow to automatically clean up old Docker images from GHCR, keeping only the 3 newest versions.

---

# 0.1.2 (2026.02.25)
## New Features
* Improved deployment process and release workflow.

---

# 0.1.0 (2026.02.25)
## New Features
* Added server side rendering infrastructure.
* Added login page with Spotify branding and a login button.
* Added slim black navigation bar showing application name and version.
* Added dashboard page as main entry point after login.
* Application version is rendered in the top menu bar.
* Added documentation pages for architecture docs, ADRs, and release notes accessible from the nav bar.

---

# 0.0.1 (2026.02.24)

The basic project skeleton was developed and deployed.

## New Features
* Basic technical documentation and project plans
* Release notes structure
* Setup for AI coding agents
* Backend skeleton using Gradle and Quarkus
* Basic setup for code quality tools

