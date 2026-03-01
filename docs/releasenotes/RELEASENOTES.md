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

