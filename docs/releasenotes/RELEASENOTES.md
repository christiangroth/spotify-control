# 0.48.4 (2026.03.13)

## Bugfixes / Chore
* add-link-to-spotify-api: Added link to Spotify Web API documentation in the Technical dropdown menu.



---

# 0.48.3 (2026.03.13)

## Bugfixes / Chore
* fix-artists-titles-readability: Artist names on /settings/playback are now readable with white text on the dark background.



---

# 0.48.2 (2026.03.13)

## Bugfixes / Chore
* create-copilot-environment-setup-yml: Added Copilot environment setup to enable building with authenticated GitHub Package Registry dependencies.
* data-migration-new-entity-structures: Migrates Track title, Album title, and Artist genre fields to updated entity structures.
* fix-gradle-dependencies: Remove Jitpack.io repository as all dependencies are available on Maven Central.
* remove-json-handling-spotify: Replaced Jackson dependency with kotlinx-serialization for Spotify API response handling.



---

# 0.48.1 (2026.03.13)

## Bugfixes / Chore
* cleanup-recently-partial-played: Cleaned up obsolete `recently_partial_played` MongoDB collection.



---

# 0.48.0 (2026.03.13)

## New Features
* sync-album-data-on-track-sync: Track enrichment now populates additional track fields (disc number, duration, track number, type) and embeds artist names directly in the track document.
* sync-album-data-on-track-sync: Album data is now synced as part of track enrichment, eliminating the separate album enrichment step.
* sync-album-data-on-track-sync: Album documents now include release date, album type, total tracks, and embedded artist information from the Spotify track API response.
* sync-album-data-on-track-sync: All artists on a track are now queued for enrichment when track details are synced.



---

# 0.47.0 (2026.03.12)

## New Features
* sync-artist-data: Artist information is now more detailed with genre classification and artist type synced from Spotify.



---

# 0.46.5 (2026.03.12)

## Bugfixes / Chore
* code-review-hexagonal-architecture: Renamed `SpotifyAccessTokenService` to `SpotifyAccessTokenAdapter` for consistent hexagonal naming conventions.
* code-review-hexagonal-architecture: Fixed test package names for web adapter tests from `adapter.web.in` to `adapter.in.web`.
* code-review-hexagonal-architecture: Rewrote arc42 documentation: removed all unimplemented feature references, added PlantUML diagrams via kroki, sorted modules alphabetically, restructured external dependencies, filled out Risks and Technical Debts.
* code-review-hexagonal-architecture: Added ADR 0007 documenting the Persistent Outbox Pattern decision.
* code-review-hexagonal-architecture: Updated coding guidelines: corrected outbox partition names, aligned test strategy description with actual implementation.



---

# 0.46.4 (2026.03.12)

## Bugfixes / Chore
* fix-expand-tape-outbox-events: Outbox event details on health page are now expanded by default and can no longer be collapsed.
* fix-expand-tape-outbox-events: Reduced fade animation duration on SSE update events.



---

# 0.46.3 (2026.03.12)

## Bugfixes / Chore
* fix-health-ui-outbox-tasks: Health UI is now updated via SSE when the number of outbox tasks changes.
* group-outgoing-requests-by-type: Outgoing Spotify API requests are now grouped by endpoint type in request stats and metrics (e.g. `/v1/tracks/{id}` instead of individual track IDs).



---

# 0.46.2 (2026.03.12)

## Bugfixes / Chore
* fix-rate-limiting-issue: Increased Spotify catalog API throttle interval from 5s to 10s to reduce rate limiting during bulk catalog enrichment.


---

# 0.46.1 (2026.03.12)

## Bugfixes / Chore
* overall-code-cleanups: Removed `/ui` prefix from all web paths and package names.
* overall-code-cleanups: Renamed `DashboardSseService` and `HealthSseService` to `DashboardSseAdapter` and `HealthSseAdapter`.
* overall-code-cleanups: Extracted common SSE connect logic into `connectSse()` helper in `sse-utils.js`.
* overall-code-cleanups: Extracted user placeholder SVG into reusable layout symbol `#icon-user-placeholder`.
* overall-code-cleanups: Removed duplicate first heading from docs pages (heading is now shown as page title only).
* overall-code-cleanups: Renamed MongoDB `CurrentlyPlayingDocument`, `RecentlyPlayedDocument`, and `RecentlyPartialPlayedDocument` to use `Spotify` prefix; renamed collection `recently_partial_played` to `spotify_recently_partial_played`.
* overall-code-cleanups: Replaced dynamic timer in `CurrentlyPlayingFetchJob` with a custom `CurrentlyPlayingSkipPredicate`.
* overall-code-cleanups: Moved `SchedulerInfoAdapter` and `CurrentlyPlayingScheduleState` to new `adapter-out-scheduler` module.



---

# 0.46.0 (2026.03.10)

## New Features
* domain-code-cleanup: Consolidated Spotify playback ports into a single SpotifyPlaybackPort interface.
* domain-code-cleanup: Consolidated Spotify catalog ports into a single SpotifyCatalogPort interface.
* domain-code-cleanup: Merged playlist tracks into SpotifyPlaylistPort.
* domain-code-cleanup: Merged outbox management ports into a single OutboxManagementPort interface.
* domain-code-cleanup: Merged playlist repository ports into a single PlaylistRepositoryPort interface.

## Bugfixes / Chore
* domain-code-cleanup: Introduced PlaylistId, ArtistId, AlbumId, and TrackId value classes.
* domain-code-cleanup: Combined related port interfaces (PlaybackPort, PlaylistPort, HealthPort, DashboardPort, UserProfilePort, LoginServicePort).



---

# 0.45.0 (2026.03.09)

## New Features
* delete-sample-api: Removed sample API endpoint (`GET /api/hello`) and all related code.



---

# 0.44.3 (2026.03.09)

## Bugfixes / Chore
* split-application-properties: Configuration properties are now declared in each owning module's application.properties instead of being centralised in application-quarkus.



---

# 0.44.2 (2026.03.09)

## Bugfixes / Chore
* fix-resuming-partition-health-ui: Resuming a paused outbox partition via the Health UI now correctly triggers event processing.

---

# 0.44.1 (2026.03.09)

## Bugfixes / Chore
* fix-health-ui: Fix resume partition button on the health page.
* fix-health-ui: Fix show events per type on the health page.

---

# 0.44.0 (2026.03.09)

## New Features
* add-resume-button-health-ui: Add button to manually resume paused outbox partitions on the health page.
* add-resume-button-health-ui: Strip hostname from outgoing HTTP request endpoints on the health page.
* add-resume-button-health-ui: Blocked countdown now shows only the countdown (no timestamp) for near-future blocks.
* add-resume-button-health-ui: Renamed various health page column headers for brevity.

---

# 0.43.0 (2026.03.09)

## New Features
* group-http-requests-by-endpoint: Outgoing HTTP requests on the health page are now grouped by endpoint instead of host.

---

# 0.42.0 (2026.03.09)

## New Features
* enhance-metrics-dashboard: Removed unused panels from the metrics dashboard.
* enhance-metrics-dashboard: Incoming HTTP requests panel no longer shows redirect responses.
* enhance-metrics-dashboard: Heap and non-heap memory "max" series are now hidden by default.
* enhance-metrics-dashboard: Spotify API request URLs are now grouped by URL pattern, so requests to the same endpoint with different IDs are aggregated together.
* enhance-metrics-dashboard: Failed and rate-limited task rate panels now display 0 instead of showing no data when no failures have occurred.
* enhance-metrics-dashboard: Partition status panel now always shows the last known state, even when no data was recorded in the selected time range.
* enhance-metrics-dashboard: Task enqueue and task processed rates are now shown in separate panels.

---

# 0.41.1 (2026.03.09)

## Bugfixes / Chore
* display-artist-name-in-settings: Artist names are now correctly displayed in playback settings, including after enrichment data is fetched from Spotify.

---

# 0.41.0 (2026.03.09)

## New Features
* enhance-artist-playback-processing: Artist names are now correctly displayed alongside their images in the playback settings list.
* enhance-artist-playback-processing: Artists are sorted alphabetically within each status group.
* enhance-artist-playback-processing: Added a filter input to the Artist Playback Processing section to search across all three lists by name (regex, case insensitive).
* enhance-artist-playback-processing: Each status column now shows the current item count, e.g. Undecided (138), updated live as the filter is applied.

---

# 0.40.3 (2026.03.09)

## Bugfixes / Chore
* fix-metrics-dashboard-queries: Fixed Grafana metrics dashboard queries.

---

# 0.40.2 (2026.03.09)

## Bugfixes / Chore
* fix-metrics-dashboard: Fixed Grafana metrics dashboard scrape configuration.

---

# 0.40.1 (2026.03.09)

## Bugfixes / Chore
* optimize-request-throttling: Increased Spotify API request throttle interval to reduce rate limiting.
* optimize-request-throttling: Increased inactive playback polling interval to reduce unnecessary requests.

---

# 0.40.0 (2026.03.09)

## New Features
* extract-util-outbox-module: Extracted outbox functionality as a standalone external library.

---

# 0.39.0 (2026.03.09)

## New Features
* extract-starters: Extracted starters functionality as a standalone external library.

---

# 0.38.1 (2026.03.09)

## Bugfixes / Chore
* fix-slow-queries-artists-tracks: Improved performance of artist and track lookups.

---

# 0.38.0 (2026.03.08)
## New Features
* move-throttling-to-http-handling: Spotify API requests are now throttled per request to reduce rate limiting.
* move-throttling-to-http-handling: Currently playing polling now adapts dynamically: every 10s when playback is active, slowing down to every 90s when no playback is detected.

---

# 0.37.0 (2026.03.08)
## New Features
* optimize-rate-limit-logging: Improved logging when outbox tasks are executed.

---

# 0.36.1 (2026.03.08)
## Bugfixes / Chore
* fix-rate-limiting-throttling: Reduced Spotify API call volume and improved rate limiting handling.

---

# 0.36.0 (2026.03.08)
## New Features
* split-settings-ui: Split settings UI into separate playlist settings (/ui/settings/playlist) and playback settings (/ui/settings/playback) pages.

---

# 0.35.2 (2026.03.07)
## Bugfixes / Chore
* extract-releasenotes-plugin: Extracted the release-notes plugin as a standalone external dependency.

---

# 0.35.1 (2026.03.07)
## Bugfixes / Chore
* add-missing-mongodb-indexes: Added missing MongoDB indexes to improve query performance.
* fix-outbox-sse-issue: Fixed outbox SSE not updating the health UI when tasks are enqueued.

---

# 0.35.0 (2026.03.07)
## New Features
* ignore-artists-for-app-playback: Artists can now be ignored for playback processing.
* ignore-artists-for-app-playback: Setting an artist to inactive removes their playback data; reactivating triggers a rebuild.
* ignore-artists-for-app-playback: Settings UI shows artists in three columns: undecided, active, and inactive.

---

# 0.34.0 (2026.03.07)
## New Features
* add-listening-stats-dashboard: Added listening stats panel to the dashboard showing listened minutes, top 3 tracks, top 3 artists, and top 3 genres for the last 30 days, ranked by listening duration.

---

# 0.33.0 (2026.03.07)
## New Features
* process-spotify-playback-data: Spotify playback data is now processed into dedicated collections for tracks, artists, and albums.
* process-spotify-playback-data: Track, artist, and album metadata are now stored in separate collections to avoid duplication.
* process-spotify-playback-data: Added a Recreate Playback Data button on the settings page to rebuild processed playback data from scratch.
* process-spotify-playback-data: Dashboard stats (totals, histogram, recently played tracks) now sourced from processed collections.

---

# 0.32.0 (2026.03.07)
## New Features
* add-github-link-menu: Added GitHub repository link (Code) to the technical dropdown menu in the navigation bar.

---

# 0.31.0 (2026.03.07)
## New Features
* create-grafana-metrics-dashboard: Added Grafana metrics dashboard covering JVM, logging, HTTP server/client, outbox, scheduler and starters.
* create-grafana-metrics-dashboard: Added Loki logs dashboard for structured log exploration in Grafana Cloud.
* create-grafana-metrics-dashboard: CI job provisions the metrics dashboard to Grafana Cloud after each release.
* create-grafana-metrics-dashboard: Added Logs and Metrics links with Grafana logo to the technical menu in the navigation bar.

---

# 0.30.2 (2026.03.07)
## Bugfixes / Chore
* fix-currently-playing-permission: Added missing OAuth scope so the currently playing endpoint can be accessed.

---

# 0.30.1 (2026.03.07)
## Bugfixes / Chore
* fix-partition-blocking-issue: Fixed an issue where tasks in a partition were not executed after being rate-limited.

---

# 0.29.0 (2026.03.06)
## New Features
* implement-throttling-feature: Outgoing Spotify API requests are now rate-limited to avoid hitting Spotify rate limits.

---

# 0.28.1 (2026.03.06)
## Bugfixes / Chore
* fix-build-playlist-track-mapping: Fixed playlist track mapping.

---

# 0.28.0 (2026.03.06)
## New Features
* design-data-collection-concept: Capture partial listens and skipped tracks to improve listening statistics.

---

# 0.27.1 (2026.03.06)
## Bugfixes / Chore
* fix-sync-playlist-button-ui: Fixed sync playlist button to only show for active playlists with green icon styling.

---

# 0.27.0 (2026.03.06)
## New Features
* cleanup-health-ui: Streamlined health UI.

---

# 0.26.0 (2026.03.06)
## New Features
* add-sync-button-to-playlist: Added a Sync button per playlist on the settings page.

---

# 0.25.0 (2026.03.06)
## New Features
* add-technical-sub-menu: Added a "Technical" dropdown sub-menu in the navigation bar, grouping Health, Loki, MongoDB, and Docs links together to reduce clutter.

---

# 0.24.2 (2026.03.06)
## Bugfixes / Chore
* fix-playlist-track-sync: Fixed playlist track sync always returning 0 items.

---

# 0.24.1 (2026.03.06)
## Bugfixes / Chore
* ensure-outbox-partition-existence: Outbox partitions are now always initialized at startup.

---

# 0.24.0 (2026.03.06)
## New Features
* enhance-dashboard-recent-tracks: Dashboard now shows recently played tracks in a new panel (configurable limit, default: 13 tracks).

---

# 0.23.1 (2026.03.06)
## Bugfixes / Chore
* add-margin-to-menu-bar-icons: Added margin to menu bar icons for improved readability.

---

# 0.23.0 (2026.03.06)
## New Features
* increase-sse-animation-duration: Increased SSE update fade animation duration to 3 seconds for dashboard and health UI.

---

# 0.22.0 (2026.03.06)
## New Features
* move-failed-outbox-tasks-to-archive: Failed outbox tasks that have exhausted all retry attempts are now moved to an archive instead of remaining in the main queue.

---

# 0.21.0 (2026.03.06)
## New Features
* add-link-to-mongodb-atlas: Add link to MongoDB Atlas Data Explorer in navigation menu bar.

---

# 0.20.0 (2026.03.06)
## New Features
* add-link-to-grafana-logs: Added a link to Grafana Cloud Logs Dashboard in the menu bar.

---

# 0.19.4 (2026.03.06)
## Bugfixes / Chore
* fix-fetch-playlist-data: Fixed playlist data fetch by using the correct Spotify API endpoint.

---

# 0.19.3 (2026.03.06)
## Bugfixes / Chore
* fix-version-bump-issue-again: Fixed automatic version bump during release.

---

# 0.19.2 (2026.03.06)
## New Features
* update-releasenotes-plugin-version-bump: Version bump is now performed automatically before the release build based on snippet types.

---

# 0.19.1 (2026.03.06)
## Bugfixes / Chore
* fix-cronjobs-ui-animation: Fixed cronjob table pulse animation and row resorting after execution.

---

# 0.19.0 (2026.03.06)
## New Features
* move-recently-played-partition: Recently-played fetching now only requests data newer than the last known playback timestamp, reducing redundant data transfer.

---

# 0.18.0 (2026.03.06)
## New Features
* simplify-blocked-until-formatting: Simplified "Blocked Until" display for outbox partitions – shows only time (HH:mm) with live countdown when less than 24h away, full date otherwise.

---

# 0.17.0 (2026.03.06)
## New Features
* display-mongodb-collection-size: MongoDB collection sizes are now displayed in kilobytes (kb) instead of bytes.
* show-blocked-until-in-outbox: Show blocked-until timestamp in outbox health when a partition is blocked.
* sort-cronjobs-by-remaining-time: Cronjobs in the health UI are now sorted by remaining time until next execution (ascending).

---

# 0.16.9 (2026.03.06)
## New Features
* enhance-cronjob-health-overview: Health page cronjob overview now shows all scheduled jobs including paused/disabled ones, and displays a Status column indicating whether each job is active or paused.

---

# 0.16.8 (2026.03.06)
## Bugfixes / Chore
* fix-spotify-playlist-tracks-adapter: Fixed 403 errors when fetching tracks of collaborative playlists.

---

# 0.16.7 (2026.03.06)
## New Features
* add-cronjob-overview-health-ui: Added cronjob overview to health UI showing all configured cronjobs with their schedule and a live countdown to the next execution.

---

# 0.16.6 (2026.03.06)
## Bugfixes / Chore
* fix-markdown-doc-links: Fixed relative links between documentation markdown files.

---

# 0.16.5 (2026.03.06)
## Bugfixes / Chore
* fix-playlist-sync-sse-event: Fixed missing SSE event when toggling playlist sync status on settings UI.

---

# 0.16.4 (2026.03.04)
## Bugfixes / Chore
* fix-login-page-redirect: Fixed login page redirect — users with a valid session are now properly redirected to the dashboard when reloading or revisiting the login page.
* fix-login-page-redirect: Session cookie is now persistent across browser restarts.

---

# 0.16.0 (2026.03.04)
## New Features
* add-mongodb-stats-health-ui: Added MongoDB collection stats (name, document count, size) and query stats (name, executions in 24h, slow query count) to the health UI, with communication and MongoDB sub-sections.

---

# 0.15.2 (2026.03.04)
## Bugfixes / Chore
* fix-cleanup-non-tracks: Podcast episodes and other non-track items are now removed from recently played history.

---

# 0.15.1 (2026.03.04)
## Bugfixes / Chore
* remove-non-owned-playlists: Playlist metadata for playlists not owned by the user is now removed.

---

# 0.14.0 (2026.03.04)
## New Features
* create-health-ui-page: Added dedicated health monitoring page at /ui/health with system health stats and real-time SSE updates.

---

# 0.13.0 (2026.03.04)
## New Features
* create-playlist-sync: Added playlist entity with full track data (tracks including artist information).
* create-playlist-sync: Playlist data sync is triggered automatically when a playlist's snapshot ID changes.
* create-playlist-sync: Playlist data sync is also triggered when a playlist is marked as active but has no synced data yet.

---

# 0.12.10 (2026.03.04)
## Bugfixes / Chore
* filter-own-users-playlists: Only store playlist metadata for playlists owned by the user (not followed playlists).

---

# 0.12.9 (2026.03.04)
## Bugfixes / Chore
* format-date-last-playlist-sync: Format the date of the last Playlist Metadata sync using German locale on the Playlists settings page.

---

# 0.12.8 (2026.03.04)
## Bugfixes / Chore
* align-settings-icon-left: Playlists icon in header is now left-aligned and uses a playlist-style icon.

---

# 0.12.7 (2026.03.04)
## Bugfixes / Chore
* split-sse-events: Dashboard updates now use fine-grained SSE events per section with partial page updates and a fade effect instead of full page reloads.

---

# 0.12.6 (2026.03.04)
## Bugfixes / Chore
* ignore-non-music-events: Podcast episodes and other non-track playback events are now ignored when fetching recently played history.

---

# 0.12.5 (2026.03.04)
## New Features
* trigger-sse-update-event: Playlist sync now triggers a Dashboard SSE refresh event when the number of playlists changes.

---

# 0.12.4 (2026.03.04)
## New Features
* split-user-profile-playlist-metadata: Playlist metadata is now stored in a separate collection, preventing user profile syncs from overwriting playlist data.

---

# 0.12.3 (2026.03.04)
## Bugfixes / Chore
* fix-sync-status-reset: Fixed scheduled playlist sync incorrectly overwriting user-configured sync status.
* fix-sync-status-reset: Fixed last sync time being reset on every sync regardless of actual changes.

---

# 0.12.1 (2026.03.04)
## New Features
* show-last-30-days-dates: Date labels under playback events histogram columns are now only shown on large screens.

---

# 0.12.0 (2026.03.04)
## New Features
* add-playlist-infos-to-dashboard: Added playlists synced stats to dashboard and reorganized dashboard sections into Spotify data and System Health.

---

# 0.11.7 (2026.03.04)
## Bugfixes / Chore
* improve-sse-handling: SSE dashboard refresh is now also triggered by changes in outgoing HTTP request metrics.

---

# 0.11.6 (2026.03.04)
## Bugfixes / Chore
* fix-new-playlists-sync-issue: New found playlists default to PASSIVE sync status instead of ACTIVE.

---

# 0.11.5 (2026.03.04)
## New Features
* tweak-playlists-sync: Playlists settings page now shows heading "Playlists" and includes a "Sync Now" button to trigger an immediate sync.

---

# 0.11.4 (2026.03.04)
## New Features
* enhance-playlist-sync-settings-ui: Add Spotify playlist sync settings with hourly sync job and settings UI to manage per-playlist sync status.

---

# 0.11.3 (2026.03.04)
## New Features
* add-prometheus-metrics: Add Prometheus metrics for outgoing Spotify API requests.
* add-prometheus-metrics: Add Spotify request stats panel to dashboard showing outgoing request counts per host (last 24h).

---

# 0.11.2 (2026.03.04)
## Bugfixes / Chore
* fix-dashboard-sse-connect: Fixed dashboard SSE connection error.

---

# 0.11.0 (2026.03.04)
## New Features
* update-header-logo-and-docs: Add logo to header bar.
* update-header-logo-and-docs: Replace Docs text in menu bar with icon.
* update-header-logo-and-docs: Make Docs link always visible when authenticated.
* update-header-logo-and-docs: Dashboard page now uses SSE for live updates.

---

# 0.10.2 (2026.03.04)
## New Features
* upgrade-java-25-kotlin-2-3-10: Upgraded to Java 25 (LTS) and Kotlin 2.3.10.

---

# 0.10.1 (2026.03.04)
## New Features
* fix-index-page-redirect: Redirect authenticated users from the index page to the dashboard UI when a valid session cookie is present.

---

# 0.10.0 (2026.03.04)
## New Features
* add-mongodb-slow-query-metrics: Add MongoDB query metrics and slow query detection.

---

# 0.9.4 (2026.03.03)
## Bugfixes / Chore
* streamline-navigation-links: Streamlined navigation links across all pages.
* streamline-navigation-links: Fixed partition stats table text color.
* streamline-navigation-links: Fixed docs page headline styling to match dashboard heading.
* streamline-navigation-links: Added Spotify favicon.
* streamline-navigation-links: Switched dashboard stats refresh to 60-second polling.

---

# 0.9.3 (2026.03.03)
## Bugfixes / Chore
* increase-polling-recently-played: Increased polling frequency for recently played tracks from every 15 minutes to every 10 minutes.

---

# 0.9.2 (2026.03.03)
## Bugfixes / Chore
* fix-docs-rendering-issue: Fixed docs markdown rendering issue.

---

# 0.9.1 (2026.03.03)
## Bugfixes / Chore
* update-partition-table-appearance: Fixed partition information table to use dark mode styling on dashboard.

---

# 0.9.0 (2026.03.03)
## New Features
* enhance-dashboard-features: Dashboard now shows a personalised greeting, playback statistics, outbox partition health, and live updates via server-sent events.

---

# 0.8.0 (2026.03.03)
## New Features
* add-outbox-archive-cleanup: Added nightly cleanup job for outbox archive that deletes documents older than a configurable number of days (default: 365).

---

# 0.7.7 (2026.03.03)
## New Features
* update-mongodb-atlas-auth-config: Updated MongoDB Atlas connection configuration.

---

# 0.7.1 (2026.03.02)
## New Features
* add-logs-metadata: Add container name and service name as log metadata labels for Loki log filtering.

---

# 0.7.0 (2026.03.02)
## New Features
* implement-monitoring-basics: Add monitoring via Grafana Alloy forwarding Prometheus metrics and structured JSON logs to Grafana Cloud.
* implement-monitoring-basics: Add custom outbox metrics (enqueued, processed, failed counters per partition).

---

# 0.6.0 (2026.03.01)
## New Features
* update-throttling-concept: Implemented outbox throttling to handle Spotify API rate limits.

---

# 0.5.0 (2026.03.01)
## New Features
* connect-outbox-for-profile-update: Connect outbox for profile update and recently played fetch.

---

# 0.4.2 (2026.02.28)
## New Features
* implement-util-outbox-module: Added outbox module with task queue, retry, deduplication, and partition management.

---

# 0.4.0 (2026.02.28)
## New Features
* implement-recently-played-fetching: Fetch and persist recently played tracks per user with duplicate suppression.

---

# 0.3.0 (2026.02.27)
## New Features
* update-user-profile: User display names are now refreshed nightly from Spotify via a scheduled job running at 4am.

---

# 0.2.2 (2026.02.27)
## Bugfixes / Chore
* fix-login-failure-error: Fix OAuth login callback returning 500 Internal Server Error on unexpected exceptions by catching them and redirecting to the error page.

---

# 0.2.0 (2026.02.27)
## New Features
* implement-error-handling-arrow: Improved error handling.

---

# 0.1.11 (2026.02.27)
## New Features
* implement-logging-system: Added logging across all relevant classes.

---

# 0.1.10 (2026.02.26)
## New Features
* implement-token-refresh: Spotify access tokens are now automatically renewed before expiry.

---

# 0.1.9 (2026.02.26)
## New Features
* implement-spotify-authentication: Implement Spotify OAuth 2.0 login with allow-listed users.

---

# 0.1.8 (2026.02.26)
## Bugfixes / Chore
* fix-build-version-ui: Fixed build version not being shown in UI in dev mode.

---

# 0.1.7 (2026.02.26)
## New Features
* implement-user-allow-list: Added user allow list support via APP_ALLOWED_SPOTIFY_USER_IDS environment variable (comma-separated Spotify user IDs).

---

# 0.1.6 (2026.02.25)
## New Features
* implement-user-persistence: Implement Spotify user persistence.

---

# 0.1.5 (2026.02.25)
## Bugfixes / Chore
* fix-build-version-display: Fixed build version not being shown in the UI in dev mode.

---

# 0.1.3 (2026.02.25)
## New Features
* add-docker-image-cleanup-workflow: Added scheduled GitHub Actions workflow to automatically clean up old Docker images from GHCR, keeping only the 3 newest versions.

---

# 0.1.2 (2026.02.25)
## New Features
* update-deployment-process: Improved deployment process and release workflow.

---

# 0.1.0 (2026.02.25)
## New Features
* add-ssr-structures: Added server side rendering infrastructure.
* add-ssr-structures: Added login page with Spotify branding and a login button.
* add-ssr-structures: Added slim black navigation bar showing application name and version.
* add-ssr-structures: Added dashboard page as main entry point after login.
* add-ssr-structures: Application version is rendered in the top menu bar.
* serve-markdown-feature: Added documentation pages for architecture docs, ADRs, and release notes accessible from the nav bar.

---

# 0.0.1 (2026.02.24)

The basic project skeleton was developed and deployed.

## New Features
* Basic technical documentation and project plans
* Release notes structure
* Setup for AI coding agents
* Backend skeleton using Gradle and Quarkus
* Basic setup for code quality tools

