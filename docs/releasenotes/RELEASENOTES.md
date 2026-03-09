# 0.40.2 (2026.03.09)

## Bugfixes / Chore
* fix-metrics-dashboard: Fixed Grafana metrics dashboard by correcting the job label in the Alloy scrape configuration so metrics are accessible with the expected `job="spotify_control"` filter.



---

# 0.40.1 (2026.03.09)

## Bugfixes / Chore
* optimize-request-throttling: Increased Spotify API request throttle interval from 2s to 5s to reduce rate limiting.
* optimize-request-throttling: Increased inactive playback polling interval from 90s to 5 minutes to reduce unnecessary requests.


---

# 0.40.0 (2026.03.09)

## New Features
* extract-util-outbox-module: Renamed util-outbox package from de.chrgroth.spotify.control.util.outbox to de.chrgroth.outbox to prepare for extraction as a standalone external library.
* extract-util-outbox-module: Replaced internal util-outbox-api and util-outbox-impl modules with the external de.chrgroth.quarkus.outbox library (domain-api, domain-impl, adapter-out-persistence-mongodb).



---

# 0.39.0 (2026.03.09)

## New Features
* extract-starters: Renamed `util-starters` package from `de.chrgroth.spotify.control.util.starters` to `de.chrgroth.starters` and added `maven-publish` configuration to prepare the module for extraction as a standalone external library.



---

# 0.38.1 (2026.03.09)

## Bugfixes / Chore
* fix-slow-queries-artists-tracks: Improved performance of artist and track lookups by replacing individual per-ID database queries with a single batch query.



---

# 0.38.0 - 2026.03.08
## New Features
* move-throttling-to-http-handling: Spotify API request throttling is now applied at the HTTP level (2s per request for enrichment and sync calls) instead of at the outbox processing level.
* move-throttling-to-http-handling: Currently playing polling now adapts dynamically: every 10s when playback is active, slowing down to every 90s when no playback is detected.

# 0.37.0 - 2026.03.08
## New Features
* optimize-rate-limit-logging: Add info-level logging when an outbox task is executed, including partition and task type.

# 0.36.1 - 2026.03.08
## Bugfixes / Chore
* fix-rate-limiting-throttling: Increased playlist tracks page size to reduce Spotify API call volume; increased ToSpotify throttle interval; improved rate-limit warn logging with endpoint URL context.

# 0.36.0 - 2026.03.08
## New Features
* split-settings-ui: Split settings UI into separate playlist settings (/ui/settings/playlist) and playback settings (/ui/settings/playback) pages.

# 0.35.2 - 2026.03.07
## Bugfixes / Chore
* extract-releasenotes-plugin: Extracted the release-notes plugin from buildSrc into a standalone Gradle plugin project to allow publishing as an external dependency.

# 0.35.1 - 2026.03.07
## Bugfixes / Chore
* add-missing-mongodb-indexes: Added missing MongoDB indexes to improve query performance.* fix-outbox-sse-issue: Fixed outbox SSE not updating the health UI when tasks are enqueued.
# 0.35.0 - 2026.03.07
## New Features
* ignore-artists-for-app-playback: Artists can now be ignored for playback processing. Setting an artist to inactive removes their playback data; reactivating triggers a rebuild. Settings UI shows artists in three columns: undecided, active, and inactive.
# 0.34.0 - 2026.03.07
## New Features
* add-listening-stats-dashboard: Added listening stats panel to the dashboard showing listened minutes, top 3 tracks, top 3 artists, and top 3 genres for the last 30 days, ranked by listening duration.
# 0.33.0 - 2026.03.07
## New Features
* process-spotify-playback-data: Process Spotify playback data into new app_playback collection combining spotify_recently_played and recently_partial_played
* process-spotify-playback-data: Introduce app_track, app_artist, and app_album collections to deduplicate track metadata
* process-spotify-playback-data: Add Recreate Playback Data button on settings page to rebuild processed playback data from scratch
* process-spotify-playback-data: Dashboard stats (totals, histogram, recently played tracks) now sourced from app_ collections
# 0.32.0 - 2026.03.07
## New Features
* add-github-link-menu: Added GitHub repository link (Code) to the technical dropdown menu in the navigation bar.
# 0.31.0 - 2026.03.07
## New Features
* create-grafana-metrics-dashboard: Added Grafana metrics dashboard covering JVM, logging, HTTP server/client, outbox, scheduler and starters.
* create-grafana-metrics-dashboard: Added Loki logs dashboard for structured log exploration in Grafana Cloud.
* create-grafana-metrics-dashboard: CI job provisions the metrics dashboard to Grafana Cloud after each release.
* create-grafana-metrics-dashboard: Added Logs and Metrics links with Grafana logo to the technical menu in the navigation bar.
# 0.30.2 - 2026.03.07
## Bugfixes / Chore
* fix-build-issue: Fix Detekt MaxLineLength violation in OAuthResource by splitting the scopes string across multiple lines.* fix-currently-playing-permission: Added missing `user-read-currently-playing` OAuth scope so the currently playing endpoint can be accessed.
# 0.30.1 - 2026.03.07
## Bugfixes / Chore
* fix-partition-blocking-issue: Fixed an issue where tasks in the to-spotify-playback partition were not executed after being rate-limited, because no delayed wakeup signal was scheduled for partitions with pauseOnRateLimit=false.
# 0.30.0 - 2026.03.06
## New Features
* rename-mongodb-collections: Renamed MongoDB collections to a clean naming scheme (app_user, spotify_playlist, spotify_playlist_metadata, spotify_recently_played, spotify_currently_playing). Added a one-time migration starter to rename existing collections on upgrade.
# 0.29.0 - 2026.03.06
## New Features
* implement-throttling-feature: Outbox partitions now support a configurable throttle interval to proactively limit outgoing request rates.
* implement-throttling-feature: The `to-spotify` partition is throttled at one request per second to avoid Spotify rate limits.
# 0.28.1 - 2026.03.06
## Bugfixes / Chore
* fix-build-playlist-track-mapping: Fixed playlist track mapping.
# 0.28.0 - 2026.03.06
## New Features
* design-data-collection-concept: Capture partial listens and skipped tracks to improve listening statistics.
# 0.27.1 - 2026.03.06
## Bugfixes / Chore
* fix-sync-playlist-button-ui: Fixed sync playlist button to only show for active playlists with green icon styling.
# 0.27.0 - 2026.03.06
## New Features
* cleanup-health-ui: Streamlined health UI.
# 0.26.0 - 2026.03.06
## New Features
* add-sync-button-to-playlist: Added a Sync button per playlist on the settings page.
# 0.25.0 - 2026.03.06
## New Features
* add-technical-sub-menu: Added a "Technical" dropdown sub-menu in the navigation bar, grouping Health, Loki, MongoDB, and Docs links together to reduce clutter.
# 0.24.2 - 2026.03.06
## Bugfixes / Chore
* fix-playlist-track-sync: Fixed playlist track sync always returning 0 items.
# 0.24.1 - 2026.03.06
## Bugfixes / Chore
* ensure-outbox-partition-existence: Outbox partition documents are now always created at startup, not only when a partition is paused.
# 0.24.0 - 2026.03.06
## New Features
* enhance-dashboard-recent-tracks: Dashboard now shows recently played tracks in a new panel (configurable limit, default: 13 tracks).
# 0.23.1 - 2026.03.06
## Bugfixes / Chore
* add-margin-to-menu-bar-icons: Added margin to menu bar icons for improved readability.
# 0.23.0 - 2026.03.06
## New Features
* increase-sse-animation-duration: Increased SSE update fade animation duration to 3 seconds for dashboard and health UI.
# 0.22.0 - 2026.03.06
## New Features
* move-failed-outbox-tasks-to-archive: Failed outbox tasks that have exhausted all retry attempts are now moved to the archive instead of remaining in the outbox collection.
# 0.21.0 - 2026.03.06
## New Features
* add-link-to-mongodb-atlas: Add link to MongoDB Atlas Data Explorer in navigation menu bar.
# 0.20.0 - 2026.03.06
## New Features
* add-link-to-grafana-logs: Added a link to Grafana Cloud Logs Dashboard in the menu bar.
# 0.19.4 - 2026.03.06
## Bugfixes / Chore
* fix-fetch-playlist-data: Fixed playlist data fetch by using the /items endpoint instead of the deprecated /tracks endpoint.
# 0.19.3 - 2026.03.06
## Bugfixes / Chore
* fix-version-bump-issue-again: Fix version bump: update in-memory project.version after writing new version to gradle.properties so that the release plugin's unSnapshotVersion task picks up the correct bumped version.
# 0.19.2 - 2026.03.06
## New Features
* update-releasenotes-plugin-version-bump: Version bump is now performed automatically before the release build based on snippet types (feature → minor, update notice → major); snippet creation tasks no longer modify gradle.properties.

# 0.19.1 - 2026.03.06
## Bugfixes / Chore
* fix-cronjobs-ui-animation: Fixed cronjob table pulse animation restarting every 500ms and cronjob row not being resorted after execution.
# 0.19.0 - 2026.03.06
## New Features
* move-recently-played-partition: Move recently-played fetching to its own outbox partition `to-spotify-recently-played` that never pauses on rate limits, and filter Spotify API calls using the last known playback timestamp to reduce redundant data transfer.
# 0.18.0 - 2026.03.06
## New Features
* simplify-blocked-until-formatting: Simplified "Blocked Until" display for outbox partitions – shows only time (HH:mm) with live countdown when less than 24h away, full date otherwise.
# 0.17.0 - 2026.03.06
## New Features
* display-mongodb-collection-size: MongoDB collection sizes are now displayed in kilobytes (kb) instead of bytes.
* show-blocked-until-in-outbox: Show blocked-until timestamp in outbox health when a partition is blocked.
* sort-cronjobs-by-remaining-time: Cronjobs in the health UI are now sorted by remaining time until next execution (ascending).
# 0.16.9 - 2026.03.06
## New Features
* enhance-cronjob-health-overview: Health page cronjob overview now shows all scheduled jobs including paused/disabled ones, and displays a Status column indicating whether each job is active or paused.
# 0.16.8 - 2026.03.06
## Bugfixes / Chore
* fix-spotify-playlist-tracks-adapter: Added missing `playlist-read-collaborative` OAuth scope to fix 403 errors when fetching tracks of collaborative playlists.
# 0.16.7 - 2026.03.06
## New Features
* add-cronjob-overview-health-ui: Added cronjob overview to health UI showing all configured cronjobs with simple class name, cron schedule and a live countdown to the next execution.
# 0.16.6 - 2026.03.06
## Bugfixes / Chore
* fix-markdown-doc-links: Fix relative links between documentation markdown files to work both offline in the git repo and when browsed via the web UI.
# 0.16.5 - 2026.03.06
## Bugfixes / Chore
* fix-playlist-sync-sse-event: Fixed missing SSE event when toggling playlist sync status on settings UI.
# 0.16.4 - 2026.03.04
## Bugfixes / Chore
* fix-login-page-redirect: Fixed login page redirect — users with a valid session are now properly redirected to the dashboard when reloading or revisiting the login page. Session cookie is now persistent across browser restarts.
# 0.16.0 - 2026.03.04
## New Features
* add-mongodb-stats-health-ui: Added MongoDB collection stats (name, document count, size) and query stats (name, executions in 24h, slow query count) to the health UI, with communication and MongoDB sub-sections.
## Bugfixes / Chore
* fix-build-issue: Fix detekt TooGenericExceptionCaught in MongoStatsAdapter by catching MongoException.
# 0.15.2 - 2026.03.04
## Bugfixes / Chore
* fix-cleanup-non-tracks: Added a bugfix starter to remove non-track items (e.g. podcast episodes) from recently played history.
# 0.15.1 - 2026.03.04
## Bugfixes / Chore
* remove-non-owned-playlists: Added bugfix starter to remove playlist metadata documents for playlists not owned by the user.
# 0.15.0 - 2026.03.04
## New Features
* implement-starters-concept: Added starters concept for one-time startup beans.
# 0.14.0 - 2026.03.04
## New Features
* create-health-ui-page: Added dedicated health monitoring page at /ui/health with system health stats and real-time SSE updates.
# 0.13.0 - 2026.03.04
## New Features
* create-playlist-sync: Added playlist entity with full track data (tracks including artist information).
* create-playlist-sync: Playlist data sync is triggered automatically when a playlist's snapshot ID changes.
* create-playlist-sync: Playlist data sync is also triggered when a playlist is marked as active but has no synced data yet.
# 0.12.10 - 2026.03.04
## Bugfixes / Chore
* filter-own-users-playlists: Only store playlist metadata for playlists owned by the user (not followed playlists).
# 0.12.9 - 2026.03.04
## Bugfixes / Chore
* format-date-last-playlist-sync: Format the date of the last Playlist Metadata sync using German locale on the Playlists settings page.
# 0.12.8 - 2026.03.04
## Bugfixes / Chore
* align-settings-icon-left: Playlists icon in header is now left-aligned and uses a playlist-style icon.
# 0.12.7 - 2026.03.04
## Bugfixes / Chore
* split-sse-events: Dashboard updates now use fine-grained SSE events per section (playback data, playlist metadata, outgoing HTTP calls, outbox partitions) with partial page updates and a fade effect instead of full page reloads.
# 0.12.6 - 2026.03.04
## Bugfixes / Chore
* ignore-non-music-events: Podcast episodes and other non-track playback events are now ignored when fetching recently played history.
# 0.12.5 - 2026.03.04
## New Features
* trigger-sse-update-event: Playlist sync now triggers a Dashboard SSE refresh event when the number of playlists changes.
## Bugfixes / Chore
* create-one-time-startup-beans-concept: Added concept document for one-time startup beans (starters).
# 0.12.4 - 2026.03.04
## New Features
* split-user-profile-playlist-metadata: Playlist metadata is now stored in a separate collection, preventing user profile syncs from overwriting playlist data.
# 0.12.3 - 2026.03.04
## Bugfixes / Chore
* fix-sync-status-reset: Fixed scheduled playlist sync overwriting user-configured sync status (ACTIVE/PASSIVE) due to stale data read before the Spotify API call. Also fixed lastSnapshotIdSyncTime being reset on every sync regardless of snapshotId changes.
# 0.12.1 - 2026.03.04
## New Features
* show-last-30-days-dates: Date labels under playback events histogram columns are now only shown on large screens.
## Bugfixes / Chore
* optimize-build-performance: Speed up build by removing verbose --info flag from CI, converting @QuarkusTest scheduler job tests to unit tests, and moving DomainOutboxContractTests to domain-api module.
# 0.12.0 - 2026.03.04
## New Features
* add-playlist-infos-to-dashboard: Added playlists synced stats to dashboard and reorganized dashboard sections into Spotify data and System Health.
# 0.11.7 - 2026.03.04
## Bugfixes / Chore
* improve-sse-handling: SSE dashboard refresh is now also triggered by changes in outgoing HTTP request metrics.
# 0.11.6 - 2026.03.04
## Bugfixes / Chore
* fix-new-playlists-sync-issue: New found playlists default to PASSIVE sync status instead of ACTIVE.
# 0.11.5 - 2026.03.04
## New Features
* tweak-playlists-sync: Playlists settings page now shows heading "Playlists" and includes a "Sync Now" button to trigger an immediate sync.
# 0.11.4 - 2026.03.04
## New Features
* enhance-playlist-sync-settings-ui: Add Spotify playlist sync settings with hourly sync job and settings UI to manage per-playlist sync status.
# 0.11.3 - 2026.03.04
## New Features
* add-prometheus-metrics: Add Prometheus metrics for outgoing Spotify HTTP requests (url, duration, response code).
* add-prometheus-metrics: Add Spotify request stats panel to dashboard showing outgoing request counts per host (last 24h).
# 0.11.2 - 2026.03.04
## Bugfixes / Chore
* fix-2071856-1165050559-9eb8f697-c4f3-4e2d-8967-17dee0927f82: Fixed HTTP 415 error on dashboard SSE connect by injecting SecurityIdentity via CDI instead of as method parameter.
# 0.11.0 - 2026.03.04
## New Features
* update-header-logo-and-docs: Add logo to header bar.
* update-header-logo-and-docs: Replace Docs text in menu bar with icon.
* update-header-logo-and-docs: Make Docs link always visible when authenticated.
* update-header-logo-and-docs: Rename arc42-EN.md to arc42.md.
* update-header-logo-and-docs: Add outbox.md and coding-guidelines serving.
* update-header-logo-and-docs: Remove ADR index page and related code.
* update-header-logo-and-docs: Change ADR URL to /ui/adr/$file.md.
* update-header-logo-and-docs: Dashboard page now uses SSE for live updates.
# 0.10.2 - 2026.03.04
## New Features
* upgrade-java-25-kotlin-2-3-10: Upgraded to Java 25 (LTS) and Kotlin 2.3.10.
## Bugfixes / Chore
* fix-current-build-issues: Fix Kotlin compiler warning about annotation default target for @ConfigProperty constructor parameters.
# 0.10.1 - 2026.03.04
## New Features
* fix-index-page-redirect: Redirect authenticated users from the index page to the dashboard UI when a valid session cookie is present.
# 0.10.0 - 2026.03.04
## New Features
* add-mongodb-slow-query-metrics: Add MongoDB query metrics, slow query detection (configurable threshold, default 250ms), and indexes for recently_played and outbox collections.
# 0.9.4 - 2026.03.03
## Bugfixes / Chore
* streamline-navigation-links: Streamlined navigation links across all pages.
* streamline-navigation-links: Fixed partition stats table text color (unreadable black-on-dark).
* streamline-navigation-links: Fixed docs page headline styling to match dashboard heading.
* streamline-navigation-links: Added Spotify favicon.
* streamline-navigation-links: Fixed marked.js webjar path (was 404).
* streamline-navigation-links: Switched dashboard stats refresh to 60-second polling.
# 0.9.3 - 2026.03.03
## Bugfixes / Chore
* increase-polling-recently-played: Increased polling frequency for recently played tracks from every 15 minutes to every 10 minutes.
# 0.9.2 - 2026.03.03
## Bugfixes / Chore
* change-to-repository-pattern: Changed MongoDB entities from active record pattern to Quarkus Panache repository pattern.
* fix-docs-rendering-issue: Fix docs markdown rendering JS error by ensuring marked.js is loaded before content scripts execute.
# 0.9.1 - 2026.03.03
## Bugfixes / Chore
* update-partition-table-appearance: Fixed partition information table to use dark mode styling on dashboard. 
# 0.9.0 - 2026.03.03
## New Features
* enhance-dashboard-features: Dashboard now shows a personalised greeting, playback statistics, outbox partition health, and live updates via server-sent events.
# 0.8.0 - 2026.03.03
## New Features
* add-outbox-archive-cleanup: Added nightly cleanup job for outbox archive that deletes documents older than a configurable number of days (default: 365).
# 0.7.7 - 2026.03.03
## New Features
* update-mongodb-atlas-auth-config: Switch MongoDB Atlas connection from connection-string to separate host, credentials and tuning configuration.
# 0.7.1 - 2026.03.02
## New Features
* add-logs-metadata: Add container name and service name as log metadata labels for Loki log filtering.
# 0.7.0 - 2026.03.02
## New Features
* implement-monitoring-basics: Add monitoring via Grafana Alloy forwarding Prometheus metrics and structured JSON logs to Grafana Cloud.
* implement-monitoring-basics: Add custom outbox metrics (enqueued, processed, failed counters per partition).

# 0.6.0 - 2026.03.01
## New Features
* update-throttling-concept: Implement outbox throttling as core feature with OutboxTaskResult sealed interface for rate-limit handling in Spotify adapters.
# 0.5.0 - 2026.03.01
## New Features
* connect-outbox-for-profile-update: Connect outbox for profile update and recently played fetch.

# 0.4.2 - 2026.02.28
## New Features
* implement-util-outbox-module: Add util-outbox module with outbox pattern support (MongoDB-backed task queue with retry, deduplication, and partition management).
# 0.4.1 - 2026.02.28
## Bugfixes / Chore
* add-spotify-throttling-concept: Add Spotify request throttling concept document (docs/plans/spotify-throttling.md).
* create-test-concept-docs: Added test-boundaries.md concept document describing the "Test Your Boundaries" approach for the hexagonal architecture.
# 0.4.0 - 2026.02.28
## New Features
* implement-recently-played-fetching: Fetch and persist recently played tracks per user with duplicate suppression.
# 0.3.0 - 2026.02.27
## New Features
* update-user-profile: User display names are now refreshed nightly from Spotify via a scheduled job running at 4am.
# 0.2.2 - 2026.02.27
## Bugfixes / Chore
* fix-login-failure-error: Fix OAuth login callback returning 500 Internal Server Error on unexpected exceptions by catching them and redirecting to the error page.
# 0.2.0 - 2026.02.27
## New Features
* implement-error-handling-arrow: Implemented error handling using Arrow.
# 0.1.11 - 2026.02.27
## New Features
* implement-logging-system: Added logging across all relevant classes (info, warn, error) using KLogging.
# 0.1.10 - 2026.02.26
## New Features
* enhance-error-handling-concept: Added error handling concept document and ADR covering DomainError enum pattern and Arrow library evaluation.
* implement-token-refresh: Implement Spotify OAuth 2.0 token refresh so access tokens are automatically renewed before expiry for all Spotify API calls.
# 0.1.9 - 2026.02.26
## New Features
* implement-spotify-authentication: Implement Spotify OAuth 2.0 login with allow-listed users.

# 0.1.8 - 2026.02.26
## Bugfixes / Chore
* fix-build-version-ui: Fixed build version not being shown in UI in dev mode by passing version via template data map instead of @TemplateGlobal.
# 0.1.7 - 2026.02.26
## New Features
* implement-user-allow-list: Added user allow list support via APP_ALLOWED_SPOTIFY_USER_IDS environment variable (comma-separated Spotify user IDs).
# 0.1.6 - 2026.02.25
## New Features
* implement-user-persistence: Implement Spotify user persistence.

# 0.1.5 - 2026.02.25
## Bugfixes / Chore
* fix-build-version-display: Fixed build version not being shown in the UI in dev mode.
# 0.1.3 - 2026.02.25
## New Features
* add-docker-image-cleanup-workflow: Added scheduled GitHub Actions workflow to automatically clean up old Docker images from GHCR, keeping only the 3 newest versions.
# 0.1.2 - 2026.02.25
## New Features
* update-deployment-process: Deployment via SCP instead of git clone on VPS; optimized GitHub Actions release workflow.

# 0.1.0 - 2026.02.25
## New Features
* add-ssr-structures: Added server side rendering infrastructure using Qute templates and Bootstrap WebJar
* add-ssr-structures: Added login page with Spotify branding and a login button
* add-ssr-structures: Added slim black navigation bar showing application name and version
* add-ssr-structures: Added dashboard page as main entry point after login
* add-ssr-structures: Application version is rendered in the top menu bar
* enhance-gradle-build-version: Serve application version dynamically in base template via app.build.version property injected from Gradle build.
* serve-markdown-feature: Added documentation pages for architecture docs, ADRs, and release notes accessible from the nav bar
* serve-markdown-feature: Added marked WebJar dependency for Markdown rendering

# 0.0.1 - 2026.02.24

The basic project skeleton was developed and deployed.

## New Features
* Basic technical documentation and project plans
* Release notes structure
* Setup for AI coding agents
* Backend skeleton using Gradle and Quarkus
* Basic setup for code quality tools

