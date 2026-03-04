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
* add-spotify-throttling-concept: Add Spotify request throttling concept document (docs/plans/spotify-throttling.md).* create-test-concept-docs: Added test-boundaries.md concept document describing the "Test Your Boundaries" approach for the hexagonal architecture.
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
* enhance-error-handling-concept: Added error handling concept document and ADR covering DomainError enum pattern and Arrow library evaluation.* implement-token-refresh: Implement Spotify OAuth 2.0 token refresh so access tokens are automatically renewed before expiry for all Spotify API calls.
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
* enhance-gradle-build-version: Serve application version dynamically in base template via app.build.version property injected from Gradle build.* serve-markdown-feature: Added documentation pages for architecture docs, ADRs, and release notes accessible from the nav bar
* serve-markdown-feature: Added marked WebJar dependency for Markdown rendering

# 0.0.1 - 2026.02.24

The basic project skeleton was developed and deployed.

## New Features
* Basic technical documentation and project plans
* Release notes structure
* Setup for AI coding agents
* Backend skeleton using Gradle and Quarkus
* Basic setup for code quality tools

