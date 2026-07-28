# 0.121.0 (2026.07.28)

## New Features
* Added a "Shallow Artists" page listing all artists currently flagged as shallow, with a one-click option to set them back to sync.
* The Catalog page now links to this new list when there are shallow artists, alongside the existing undecided-artists link.



---

# 0.120.0 (2026.07.26)

## New Features
* Added a language toggle (EN / pseudo-locale) to the navbar; all UI text now goes through translatable message bundles instead of being hardcoded in templates.



---

# 0.119.1 (2026.07.26)

## Bugfixes / Chore
* Fixed the Playlist Checks page sometimes showing "no results" even though checks had already run and been saved.



---

# 0.119.0 (2026.07.26)

## New Features
* Replaced the flat dark background with a subtle green ambient gradient across the whole app, and added a matching dot-pattern hero on the login page.



---

# 0.118.2 (2026.07.25)

## Bugfixes / Chore
* Internal test suite cleanup; no user-facing behavior changed.



---

# 0.118.1 (2026.07.25)

## Bugfixes / Chore
* Switched the frontend from hand-copied inline SVG icons to the Bootstrap Icons WebJar dependency, so icons stay easier to maintain and consistent going forward.
* Added a Docker `HEALTHCHECK` to the JVM and native container images that polls the application's readiness endpoint, so orchestrators can detect and restart unhealthy containers automatically.
* Fixed the "View In Explore" link on the Grafana Quarkus Logs dashboard so it uses the correct Loki label and no longer leads to an empty view.
* Hardened the CI pipeline: concurrent runs on the same branch are guarded, and the Gradle cache is only written from `main`.



---

# 0.118.0 (2026.07.23)

## New Features
* Playlist checks can now be fixed selectively: the checks page shows the violation count per playlist and opens a list where individual violations can be excluded before fixing, instead of only fixing everything at once.



---

# 0.117.1 (2026.07.23)

## Bugfixes / Chore
* Fixed the "Track From Latest Release" playlist check still falsely flagging tracks as outdated after a prior fix, when the correct current album had not yet been fully considered.



---

# 0.117.0 (2026.07.23)

## New Features
* Added a button to manually trigger playlist checks for all active playlists on the Playlist Checks page.
* Added a button next to each check type on the Playlist Checks page to trigger just that check for all active playlists.



---

# 0.116.5 (2026.07.23)

## Bugfixes / Chore
* Fixed the "Track From Latest Release" playlist check falsely flagging tracks when an album gets re-released as a deluxe, live, or anniversary edition.


---

# 0.116.4 (2026.07.23)

## Bugfixes / Chore
* Fixed the Artists column on the playlist settings page always showing "–" instead of the artist counts.
* Removed a duplicate, unreachable playlist settings page that was left over from an earlier change.



---

# 0.116.3 (2026.07.23)

## Bugfixes / Chore
* Fixed the "Artists" column on the playlist settings page failing to show a count when playlist data was incomplete.



---

# 0.116.2 (2026.07.23)

## Bugfixes / Chore
* Disabled the outbox archive: completed and failed outbox tasks are no longer kept for auditing, and any previously archived entries are cleared automatically.
* Updated the outbox library dependency to 0.8.4, which now correctly stops archiving when disabled instead of only skipping the retention cleanup.



---

# 0.116.1 (2026.07.22)

## Bugfixes / Chore
* Moved the "Rebuild Aggregations" action from the Playback page to the Stats page.
* Moved the "Refresh Profile" action from the Playback page to a small reload icon in the Dashboard's welcome heading.



---

# 0.116.0 (2026.07.22)

## New Features
* Added manual triggers for rebuilding playback aggregations, refreshing your Spotify profile, and polling playback data immediately, instead of waiting for the next scheduled run.

## Bugfixes / Chore
* The "Sync Missing Artists" action is now also available on the Playback page, which is the page linked from the main navigation. It was previously only reachable on an unlinked settings page.
* Removed the unlinked, duplicate Playback settings page; the Playback page reachable from the navigation now covers the same actions.



---

# 0.115.0 (2026.07.22)

## New Features
* Added a "Sync Missing Artists" action on the Playback Settings page to sync only artists referenced in playback history that are not yet in the catalog.



---

# 0.114.6 (2026.07.22)

## Bugfixes / Chore
* Catalog wipe, catalog re-sync, playlist "Sync Now" and playlist check "Fix" actions are now enqueued and processed asynchronously in the background instead of blocking the request, consistent with all other Spotify-facing operations.



---

# 0.114.5 (2026.07.22)

## Bugfixes / Chore
* Fixed the playlists table on the Playlists settings page overflowing the screen width and causing a horizontal page scrollbar on small screens.



---

# 0.114.4 (2026.07.22)

## Bugfixes / Chore
* Fixed wide tables in the documentation viewer (e.g. arc42 docs) causing horizontal scrolling of the whole page on small screens; tables now scroll within their own container instead.



---

# 0.114.3 (2026.07.22)

## Bugfixes / Chore
* Routed the "Set Sync" and "Set Shallow" artist actions through the outbox so they are processed reliably instead of blocking the request.
* Renamed the "Guessed Status" column on the Artist Settings page to "Assumption".



---

# 0.114.2 (2026.07.22)

## Bugfixes / Chore
* Fixed the "Set Sync" and "Set Shallow" buttons on the Artist Settings page, which stopped reacting to clicks.



---

# 0.114.1 (2026.07.22)

## Bugfixes / Chore
* Simplified the arc42 "Requirements Overview" section, removing internal implementation details.
* Corrected the arc42 documentation of the playback polling interval and external-system interaction directions.
* Reworked the Business and Technical Context sections as definition lists with clearer descriptions.
* Clarified in the architecture constraints that the outbox rule covers domain operations too, and that Spotify throttling is proactive.



---

# 0.114.0 (2026.07.22)

## New Features
* Added a "Requeue" button per partition on the outbox viewer to clear stuck, overdue tasks without restarting the app.



---

# 0.113.2 (2026.07.22)

## Bugfixes / Chore
* Added test coverage verifying the playlist Artists column correctly reflects artists already present in the catalog.



---

# 0.113.1 (2026.07.19)

## Bugfixes / Chore
* Fixed the Playlists settings page showing "–" in the Artists column for playlists with zero distinct artists instead of the actual count.



---

# 0.113.0 (2026.07.18)

## New Features
* Added a link on the Stats page's Day view to jump straight to the Playback Events for that same date.



---

# 0.112.2 (2026.07.18)

## Bugfixes / Chore
* Fixed the Artists column on the playlist settings page showing "–" for playlists whose artist count could not be computed.



---

# 0.112.1 (2026.07.18)

## Bugfixes / Chore
* Clicking a bar in the Playback Events per Day chart on the dashboard now leads to the Stats page for that exact day, since that is the data the chart visualizes, instead of the Playback stats page.



---

# 0.112.0 (2026.07.18)

## New Features
* Added an "Artists" column to the playlist settings page, showing the number of distinct artists per playlist and how many of them are not yet in the catalog.

## Bugfixes / Chore
* Clicking the Playback Events per Day chart on the dashboard now leads to the Playback stats page instead of jumping straight to the raw events, matching the drill-down from Playback stats to Playback Events.
* The Playback page stats tiles now link to the Playback Events view for the date selected on the dashboard, when available.



---

# 0.111.7 (2026.07.18)

## Bugfixes / Chore
* Newly discovered artists already present on a playlist that is actively synced are now marked as fully synced right away, instead of first requiring manual confirmation.
* Clicking the Playback Events per Day chart now correctly keeps the Playback tile highlighted in navigation.
* The Playback page stats tiles now link to the Playback Events view for the current date.



---

# 0.111.6 (2026.07.18)

## Bugfixes / Chore
* Fixed inconsistent page widths: all pages now share the same responsive content container, so lines and tables no longer stretch edge-to-edge on wide monitors while still using the full width on small and medium screens.
* Restricted the Claude Code CI workflow so it only runs for actions performed by the repository owner.



---

# 0.111.5 (2026.07.18)

## Bugfixes / Chore
* Reviewed and refreshed the architecture documentation: arc42 now reflects the actual current implementation (outbox partitions, scheduler jobs, catalog sync, playback aggregation, playlist checks) instead of an outdated design, and four new ADRs capture previously undocumented decisions (shallow artists, partial-play detection, playlist checks framework, Mermaid diagram rendering).
* Documentation diagrams now render as actual Mermaid diagrams, both on GitHub and in the app's own Docs page, instead of static pre-rendered images.
* Restricted the Claude Code CI workflow so it only runs for actions performed by the repository owner.



---

# 0.111.4 (2026.07.17)

## Bugfixes / Chore
* Fixed inconsistent page widths: all pages now share the same responsive content container, so lines and tables no longer stretch edge-to-edge on wide monitors while still using the full width on small and medium screens.



---

# 0.111.3 (2026.07.15)

## Bugfixes / Chore
* Moved playlist, catalog, outbox, MongoDB collection, and application info metrics into the dedicated metrics module, keeping the metrics caching separate from domain logic.
* MongoDB collection size stats are now cached and shared, so the health page/live updates no longer trigger a separate MongoDB query for each read.
* Kept the outbox archive cleanup job active and reduced its retention to 1 day, keeping the archive collection small until the underlying library issue is fixed.
* Activating a playlist for sync now automatically confirms any artist that was previously waiting for manual review and is present on that playlist.



---

# 0.111.2 (2026.07.15)

## Bugfixes / Chore
* Fixed slow responses for health page fragments and navbar status icons.



---

# 0.111.1 (2026.07.15)

## Bugfixes / Chore
* Reorganized the internal HTTP adapter structure, separating web pages from response-time metrics collection to prepare for future metrics caching.



---

# 0.111.0 (2026.07.15)

## New Features
* Added slow HTTP response logging: every REST, fragment, and page endpoint now logs a warning if it takes 150ms or longer to respond, naming the operation and, for endpoints doing multiple internal steps, each step's individual duration.
* Added HTTP response duration and slow-response-rate metrics to the Grafana monitoring board.



---

# 0.110.2 (2026.07.13)

## Bugfixes / Chore
* Fixed the Artist Settings page taking many seconds to load by no longer computing album and track counts per artist; it now shows a plain artist list.
* Fixed the Artist Settings page taking many seconds to load by no longer computing album and track counts per artist; it now shows a plain artist list.
* Reduced background load on the database by spacing out the recurring statistics jobs instead of running them all at once, and by caching playlist statistics instead of querying them on every metrics scrape.



---

# 0.110.1 (2026.07.13)

## Bugfixes / Chore
* Fixed the outbox viewer background refresh continuously querying the database every 15 seconds, even when nobody was viewing the outbox viewer page, causing repeated slow-query warnings.



---

# 0.110.0 (2026.07.12)

## New Features
* Added shallow artists: mark an artist to only sync its basic info, without albums or tracks, and exclude its playback from stats.
* Newly discovered artists start out as an assumption (sync or shallow) based on whether they were found via a synced playlist.
* The catalog page lets you switch an artist between full sync and shallow at any time; switching to shallow removes its albums and tracks and rebuilds stats.
* Added an Artist Settings page listing artists awaiting a sync decision, with buttons to confirm them as sync or shallow. The catalog page and navigation now show a count of undecided artists.

## Bugfixes / Chore
* Removed dangling links to a deleted, temporary review document from an architecture decision record.



---

# 0.109.1 (2026.07.10)

## Bugfixes / Chore
* Changed the Catalog nav icon to a book and the Playlists nav icon to a document.



---

# 0.109.0 (2026.07.10)

## New Features
* Added a cross-page navigation bar with Dashboard, Stats, Playlists, Checks, Catalog and Playback links, now visible on every page.
* The current page is highlighted green in the navigation bar.
* The navigation bar adapts to screen size: icons only on the smallest screens so all links fit in one line, text only on small screens, and icons with text on larger screens.



---

# 0.108.4 (2026.07.10)

## Bugfixes / Chore
* Playback event totals shown on the dashboard are now summed by the database instead of the application, reducing memory use as listening history grows.



---

# 0.108.3 (2026.07.10)

## Bugfixes / Chore
* Prefixed release notes version numbers with "v" (e.g. "v0.107") in the Release Notes page.



---

# 0.108.2 (2026.07.10)

## Bugfixes / Chore
* Improved the release notes accordion header: removed the redundant ".x" suffix, moved the date to the right without parentheses, and moved the version list into the panel body.



---

# 0.108.1 (2026.07.10)

## Bugfixes / Chore
* Fixed the release notes page: the newest 3 version groups are now expanded by default, bullet point text is readable again, and the accordion headers are now at least as large as the section headings inside them.



---

# 0.108.0 (2026.07.10)

## New Features
* The Release Notes page now groups patch releases together by minor version, with collapsible panels showing the newest version group expanded by default.

## Bugfixes / Chore
* Added the groundwork for parsing and grouping release notes by minor version, in preparation for a more compact release notes view.



---

# 0.107.16 (2026.07.10)

## Bugfixes / Chore
* Renamed the "Users & Playlists" row on the Overview dashboard to "Playlists" and made its three panels equal width.
* Unified the slow MongoDB query warning to a single consistent log format.



---

# 0.107.15 (2026.07.10)

## Bugfixes / Chore
* Fixed a slow database query on the Catalog Sync page by computing per-artist album counts directly in the database instead of loading full album records.



---

# 0.107.14 (2026.07.10)

## Bugfixes / Chore
* The displayed user name is now cached instead of being fetched from the database on every page load.



---

# 0.107.13 (2026.07.10)

## Bugfixes / Chore
* Replaced the outdated multi-user performance review with a new one reflecting the single-user architecture, focused on the data model and caching gaps.
* Sped up catalog artist/album search with a database index.
* Catalog statistics on the dashboard and catalog page now come from a shared cache instead of running fresh database queries on every page load.



---

# 0.107.12 (2026.07.10)

## Bugfixes / Chore
* Removed the "Active Users" panel and its underlying metric from the Grafana overview dashboard, since the application only ever has one user.
* Simplified several Grafana playback panels that grouped by user, since there is only ever one user to show.
* Updated the architecture documentation and ADR for the single-user redesign to describe only the current state, and removed the completed migration plan content, keeping only remaining simplification ideas.



---

# 0.107.9 (2026.07.09)

## Bugfixes / Chore
* Simplified the playback, playback-aggregation, currently-playing, recently-played, and playlist database collections to no longer be scoped by user, since the app only ever supports one.



---

# 0.107.8 (2026.07.09)

## Bugfixes / Chore
* Continued the single-user simplification: playback, playlist, and catalog no longer require selecting a user internally, since the app only ever supports one.



---

# 0.107.7 (2026.07.09)

## Bugfixes / Chore
* Simplified the dashboard and its live-update stream to rely on there always being exactly one application user.



---

# 0.107.6 (2026.07.09)

## Bugfixes / Chore
* Simplified the user profile update flow to rely on there always being exactly one application user.



---

# 0.107.5 (2026.07.09)

## Bugfixes / Chore
* Simplified internal scheduling and catalog sync logic as part of the ongoing single-user architecture migration.



---

# 0.107.4 (2026.07.09)

## Bugfixes / Chore
* Added a documented plan and architecture decision for simplifying the application to a strict single-user design.
* Login no longer requires being on an allow-list; any Spotify account can now log in.
* Once an account has logged in, login attempts from any other Spotify account are rejected to prevent a different account from taking over the instance.



---

# 0.107.3 (2026.07.08)

## Bugfixes / Chore
* Added a documented performance review with concrete follow-up action items.* Added a documented performance review with concrete follow-up action items.* Improved performance of the playlist settings page, catalog search, and stats page as the amount of data grows.
* Improved reliability of catalog metrics under load.



---

# 0.107.2 (2026.07.08)

## Bugfixes / Chore
* Fixed a slow `outbox.task.findByPartition` query on the Outbox Viewer page by caching partition task lists for 15 seconds instead of re-querying on every page load or partition-change notification.



---

# 0.107.1 (2026.07.08)

## Bugfixes / Chore
* Fixed the Catalog Sync timeline not showing the album id, album name and track count for album sync entries.



---

# 0.107.0 (2026.07.08)

## New Features
* Added a "Catalog Sync" page under the Tools menu showing a timeline of the artists and albums most recently synced from Spotify, newest first, with a "load more" button to page through older entries.

## Bugfixes / Chore
* Sped up the dashboard by trimming the data fetched for the last-30-days playback stats and adding a missing MongoDB index, cutting the two slowest queries behind the "Slow MongoDB query" warnings.
* Fixed slow `outbox.partition.findAll` queries by having the health page and its live-updating widgets share the same cached outbox partition stats used for metrics, instead of each triggering its own query.
* Fixed slow Stats page loading by capping the number of top tracks/artists/albums stored per week/month/quarter/year aggregation, instead of persisting one entry per distinct item ever played in that period.



---

# 0.106.13 (2026.07.08)

## Bugfixes / Chore
* Sped up the dashboard by switching a slow aggregation query to a fast, index-backed lookup, removing a redundant query, and only fetching full track details for the tracks actually shown in the top lists.



---

# 0.106.12 (2026.07.08)

## Bugfixes / Chore
* Updated the outbox library dependency, which fixes frequent "Slow MongoDB query" warnings for the event type count query by maintaining per-partition counts incrementally instead of recomputing them on every read.



---

# 0.106.11 (2026.07.07)

## Bugfixes / Chore
* Reduced the number of catalog lookups needed to render the Stats page and the Dashboard's listening stats, cutting slow MongoDB queries for track/album/artist details.



---

# 0.106.10 (2026.07.07)

## Bugfixes / Chore
* Fixed slow dashboard loading caused by inefficient playback statistics queries.



---

# 0.106.9 (2026.07.07)

## Bugfixes / Chore
* Restored the repeating status panels and dashboard fixes that were rolled back while chasing the "No Data" issue, since they were not the actual cause.
* Fixed the real root cause: MongoDB collection size and Outbox pending-count metrics now refresh in the background every 15 seconds instead of querying MongoDB/the outbox during Prometheus scrapes, so a slow query can no longer make the whole metrics endpoint time out.



---

# 0.106.8 (2026.07.07)

## Bugfixes / Chore
* Reverted the Technical Overview dashboard's Starter and Outbox Partition status panels back to fixed, non-repeating panels, since the native Grafana repeating-panel approach kept causing "No Data" on dashboard load.



---

# 0.106.7 (2026.07.07)

## Bugfixes / Chore
* Reverted the recent Grafana dashboard and Outbox/MongoDB metrics changes back to the last known-good state, after several follow-up fixes failed to fully resolve dashboards showing "No Data".



---

# 0.106.6 (2026.07.06)

## Bugfixes / Chore
* Reduced redundant outbox pending-count queries that were causing frequent slow query reports.
* Fixed the broken Grafana icon in the Tools menu.



---

# 0.106.5 (2026.07.06)

## Bugfixes / Chore
* Fixed a bug where all Grafana dashboards showed "No Data" because collecting the Outbox and MongoDB collection size metrics was slow enough to make the metrics endpoint time out.



---

# 0.106.4 (2026.07.06)

## Bugfixes / Chore
* Fixed dashboards showing "No Data" for the Starter and Outbox Partition status panels right after opening them, without needing to change the time range first.



---

# 0.106.3 (2026.07.06)

## Bugfixes / Chore
* Overview dashboard now shows Pending Outbox Tasks and Outbox Partition Status as one auto-generated panel per partition instead of a combined stat, matching the Technical Overview dashboard.
* Shortened Grafana dashboard cross-links and the Tools menu entries to "Domain", "Overview", "Technical" and "Logs".



---

# 0.106.2 (2026.07.06)

## Bugfixes / Chore
* Technical Overview dashboard now uses repeating panels for Starter Status and Outbox Partition Status, so each starter/partition automatically gets its own panel as they are added or removed.



---

# 0.106.1 (2026.07.06)

## Bugfixes / Chore
* Fixed the Outbox Partitions quick status on the Technical Overview dashboard, which could show OK even while a partition was paused.
* Fixed the Outbox Partitions status panel on the Technical Overview dashboard, where the label text was hidden because it didn't fit the panel.
* Fixed the "Starter Overall Status" panel on the Technical Overview dashboard so starter names are no longer hidden when many starters are shown.



---

# 0.106.0 (2026.07.06)

## New Features
* The Technical Overview dashboard now shows the size of every MongoDB collection as a graph over time.



---

# 0.105.0 (2026.07.06)

## New Features
* Renamed the "Quarkus Metrics" dashboard to "Technical Overview" and the "Domain Metrics" dashboard to "Domain Overview", and moved all Grafana dashboards into a shared "SpCtl" folder.
* The Technical Overview dashboard now shows a small OK/NOK status panel per starter instead of a cramped table, plus two new status panels (Starters, Outbox Partitions) next to CPU usage.
* Added a graph showing how many outbox events of each type are currently queued, so paused partitions draining their backlog are visible over time.
* The Domain Overview dashboard now also shows catalog size (artists/tracks/albums) as a graph over time, not just as numbers.
* Reordered the Technical Overview dashboard sections and grouped the Outbox rate panels into a single row.

## Bugfixes / Chore
* Fixed the Overview dashboard's Starter Overall Status, which now shows a single OK/NOK instead of one tile per starter.
* Fixed the Overview dashboard's Errors (Last Hour) panel showing "No Data" instead of 0 when there were no errors.
* Fixed the Pending Outbox Tasks panels showing 0 despite a real backlog after an app restart, by backing them with the actual persisted task counts instead of a counter difference.
* Fixed the Grafana logging dashboard showing no logs by default because of a broken "Line Not Contains" filter.



---

# 0.104.0 (2026.07.06)

## New Features
* Added a new Overview dashboard in Grafana showing active users, tracked playlists, pending outbox tasks, recent errors, and how fresh playback/playlist syncing is, at a glance.
* Added a link to the new Overview dashboard in the Tools menu.
* Fixed several panels in the Technical Metrics Grafana dashboard that never showed any data (app version, scheduled job timings, paused outbox tasks).
* Removed a broken log panel from the Technical Metrics dashboard and added quick links between the Overview, Technical Metrics and Logs dashboards instead.
* Added a new Domain Metrics dashboard in Grafana showing playback ingestion rates, playlist mirror freshness, duplicate/album-upgrade fixes, catalog size, and listening-stats aggregation freshness.
* Added a link to the new Domain Metrics dashboard in the Tools menu.
* The Grafana logging dashboard now has a log level filter (select one or more levels).
* The logging dashboard's free-text search was replaced with two filters, "Line Contains" and "Line Not Contains", each accepting multiple values separated by `|`.

## Bugfixes / Chore
* The three Grafana metrics dashboards and the logging dashboard are now fully cross-linked with each other.
* Fixed the Grafana icons in the Tools menu links rendering inconsistently sized compared to the other menu icons.
* Removed the outdated dashboard planning document, now that its proposals have been implemented.



---

# 0.103.1 (2026.07.03)

## Bugfixes / Chore
* Fixed the Album and Album Tracks debug requests on the Spotify Debug page: albums can now be searched directly by title instead of only through an artist search.
* Spotify Debug page now renders results as a syntax-highlighted, collapsible/expandable JSON tree instead of a plain text dump.



---

# 0.103.0 (2026.07.03)

## New Features
* The Spotify Debug page now performs the real Spotify requests instead of returning dummy data.
* Debug requests are grouped into tabs by domain: User, Playback, Playlist and Catalog.



---

# 0.102.0 (2026.07.03)

## New Features
* Added a new "Spotify Debug" page (linked from the Tools menu) for manually triggering the backend's Spotify requests and inspecting the raw JSON result.
* The debug page lets you pick artists, albums and playlists by name via a search input, instead of typing raw Spotify IDs.



---

# 0.101.4 (2026.07.02)

## Bugfixes / Chore
* Reduced the number of Spotify requests needed during catalog sync, helping avoid rate limiting.



---

# 0.101.3 (2026.07.01)

## Bugfixes / Chore
* Live-updating sections (e.g. Health UI) no longer fade the whole block in and out on every update.
* Only the parts of the page that actually changed are highlighted briefly, so content stays readable while updates happen in the background.
* Dashboard no longer fades its playback stats sections on every playback poll while a track is playing; it now only refreshes them when the recently played or listening stats actually change.



---

# 0.101.2 (2026.07.01)

## Bugfixes / Chore
* Fixed the "Outbox Partitions" section on the Config UI (`/health`) not refreshing live via SSE.



---

# 0.101.1 (2026.06.30)

## Bugfixes / Chore
* Catalog resync and outbox/catalog wipe recovery now discover artists the same way as live playback and playlist sync, fixing an inconsistency that previously caused far more sync events than expected after a catalog wipe.



---

# 0.101.0 (2026.06.30)

## New Features
* Catalog UI now shows an info icon per artist and album revealing what triggered its sync (playback, playlist, artist discography, or manual resync), resolved to readable names instead of raw IDs.
* Outbox UI now has a Wipe button to clear stuck outbox events and partitions, e.g. after wiping the catalog.



---

# 0.100.8 (2026.06.26)

## Bugfixes / Chore
* Re-wiped the outbox queue on next startup to clear the backlog caused by the previous catalog sync fanout.



---

# 0.100.7 (2026.06.26)

## Bugfixes / Chore
* Catalog sync no longer chases down every artist that collaborated on a synced album, which previously caused the sync queue to grow unbounded and hit Spotify rate limits.



---

# 0.100.6 (2026.06.10)

## Bugfixes / Chore
* Catalog re-sync now rebuilds an empty catalog from existing playback history.



---

# 0.100.5 (2026.06.10)

## Bugfixes / Chore
* Disabled sync of additional artist IDs on tracks to prevent excessive artist and album syncing.



---

# 0.100.4 (2026.06.10)

## Bugfixes / Chore
* Outbox viewer UI is now updated in real time via SSE when outbox task counts change.
* Outbox viewer content is refreshed automatically on SSE reconnect.



---

# 0.100.3 (2026.06.10)

## Bugfixes / Chore
* Improved album track sync robustness for incomplete Spotify API album payloads.
* Added clearer error details in logs when album sync requests fail.



---

# 0.100.2 (2026.06.09)

## Bugfixes / Chore
* Merged catalog outbox partitions `to-spotify-catalog-artist` and `to-spotify-catalog-album` back into a single `to-spotify-catalog` partition to reduce complexity and rate limiting risk.
* Paused the nightly artist catalog sync job.
* Added a one-time starter to enqueue artists found in playback data for sync.
* Added a cleanup starter to remove the old catalog artist and album outbox partition data from the database.



---

# 0.100.1 (2026.05.19)

## Bugfixes / Chore
* Cleaned up excessive INFO logging by removing per-poll, per-item, guard-clause, and repository adapter log statements.



---

# 0.100.0 (2026.05.18)

## New Features
* Spotify artist album sync now includes singles in addition to albums.
* Catalog outbox split into separate artist and album partitions with dedicated rate limits.
* Artist catalog requests are throttled at 60 seconds; album catalog requests use the default 10 second interval.



---

# 0.99.3 (2026.05.18)

## Bugfixes / Chore
* Optimized playlist track append: replaced slow read-then-write with a single atomic MongoDB push operation.



---

# 0.99.1 (2026.05.18)

## Bugfixes / Chore
* Fixed the main branch build after the logs viewer test setup fell out of sync with the resource constructor.



---

# 0.99.0 (2026.05.18)

## New Features
* Outbox playlist sync events now run on a dedicated partition, separate from catalog sync events.
* User profile updates now run on their own dedicated outbox partition.

## Bugfixes / Chore
* Improved aggregation stats cards by using richer catalog entries with artwork and metadata for tracks, albums, and artists.
* Top tracks now include album and artist information in the Stats and Dashboard listening sections.
* Daily aggregation cards no longer show the day/time-window activity chart.



---

# 0.98.1 (2026.05.14)

## Bugfixes / Chore
* Fix album sync failing when Spotify omits `available_markets` or `album_group` fields in artist discography responses.



---

# 0.98.0 (2026.05.13)

## New Features
* Spotify OpenAPI spec is now stored in the project under `adapter-out-spotify/src/main/resources/spotify-openapi.yaml`.
* A weekly GitHub Actions workflow now detects changes to the Spotify Web API spec and creates a GitHub issue when updates are available.



---

# 0.97.0 (2026.05.13)

## New Features
* Stats now shows Top Tracks, Top Albums, and Top Artists with images and listened duration.
* Stats activity by day and time window is now shown as an ordered bar chart from Monday 0-6 to Sunday 18-0.



---

# 0.96.0 (2026.05.13)

## New Features
* Stats page now uses tabs to show only one aggregation period at a time (Day, Week, Month, Quarter, Year).
* Playback time per aggregation period is shown as "X h Y min listened (N Events)".
* Top Tracks, Albums and Artists are now shown in that order, styled like the dashboard, and expanded from 3 to 5 entries.
* Top section headings dynamically show the count of displayed entries vs. total distinct entries (e.g. "Top Tracks (5/42)").



---

# 0.95.0 (2026.05.13)

## New Features
* Added album-based playback aggregation entries and distinct album counts.
* Updated aggregation backfill starter so all periods are recalculated with album metrics.



---

# 0.94.1 (2026.05.13)

## Bugfixes / Chore
* Catalog sync stops fetching additional album pages for an artist when all albums on the current page are already known, avoiding unnecessary Spotify API calls during re-syncs.



---

# 0.94.0 (2026.05.12)

## New Features
* Added aggregation stats content on the Stats page with day, week, month, quarter, and year views.
* Added compact period cards per aggregation view including top artists, albums, tracks, and activity details.



---

# 0.93.0 (2026.05.12)

## New Features
* Dashboard now shows flat navigation tiles (Stats, Playlists, Checks, Catalog, Playback) as the first row instead of live stats tiles.
* New pages are available at /stats, /playlists/settings, /playlists/checks, and /playback.
* Catalog page now shows individual stats tiles for Artists, Albums, and Tracks.
* Playback page now shows event count tiles (Last 30 Days and Total) alongside the existing settings.



---

# 0.92.0 (2026.05.12)

## New Features
* Dashboard listening stats now include Top Albums, showing the top 5 albums by play duration over the last 30 days.



---

# 0.91.1 (2026.05.11)

## Bugfixes / Chore
* Catalog sync no longer hammers all album pages in a single burst for artists with many albums.
* Each page of the artist albums sync is now processed as a separate outbox task, respecting rate limit delays.



---

# 0.91.0 (2026.05.08)

## New Features
* Logs UI now offers an additional grouped view by class and log type.
* The default Logs UI view remains chronological.



---

# 0.90.1 (2026.05.08)

## Bugfixes / Chore
* Fixed Logs UI entries so the time is visible even if client-side formatting does not run.



---

# 0.90.0 (2026.05.07)

## New Features
* Added a new Logs UI in the Technical menu to view recent WARN and ERROR application logs.



---

# 0.89.0 (2026.04.14)

## New Features
* Listening durations on the dashboard are now displayed in a more readable format: values of 60 minutes or more are shown as "X h Y min" instead of a plain minute count.



---

# 0.88.1 (2026.04.14)

## Bugfixes / Chore
* Fixed daily playback aggregations reporting inflated event counts by correcting the time-window query used for each day's data.
* Fixed playback aggregations incorrectly not including data from days with late-arriving items.
* Aggregations are rebuilt to correct historical data.



---

# 0.88.0 (2026.04.12)

## New Features
* Removed "Artists on this day" section from the Playback Event Viewer.
* Playback events in the viewer now show compact structured items with track, artist, album, start time and duration instead of raw JSON.



---

# 0.87.1 (2026.04.12)

## Bugfixes / Chore
* Fixed rendering error in playback event viewer when displaying artists on a given day.



---

# 0.87.0 (2026.04.12)

## New Features
* Dashboard statistics (playback counts, listened minutes, top tracks and artists) are now derived from pre-computed aggregations for faster and more consistent display.
* Today's aggregation is automatically updated after each playback data refresh, keeping current-day stats up to date.



---

# 0.86.0 (2026.04.12)

## New Features
* Errors and exceptions are now displayed as a full error page with the exception type, message, and stack trace instead of a generic "Internal Server Error".



---

# 0.85.0 (2026.04.12)

## New Features
* Artists can now be blocked from playback aggregations, so they no longer appear in statistics.
* Block and unblock actions are available in the Catalog Browser and Playback Event Viewer.
* Blocking or unblocking an artist automatically triggers a full aggregation rebuild.



---

# 0.84.0 (2026.04.09)

## New Features
* Playback data is now aggregated nightly (daily), weekly, monthly, quarterly, and yearly.
* Aggregations capture total playback duration, distinct artist count, top artist and track rankings, per-artist and per-track durations, and activity times by weekday and time window.



---

# 0.83.0 (2026.04.09)

## New Features
* Combined currently playing and recently played fetches into a single playback detection job.

## Bugfixes / Chore
* Removed unused `DatabaseMigrationPort` and `DatabaseMigrationAdapter` dead code.
* Fixed outdated documentation references to htmx (replaced by vanilla JS fetch API).
* Removed broken links to non-existent documentation files from README.



---

# 0.82.2 (2026.04.08)

## Bugfixes / Chore
* Removed artist sync status feature.
* All artists from playlists and playback events are now always synchronized to the catalog.
* Fixed syncing large playlists that fail when Spotify returns null artist or album IDs.



---

# 0.82.1 (2026.04.08)

## Bugfixes / Chore
* Fixed column order in the Outbox Viewer table.
* Removed redundant Event Priority column; priority badge is now shown inline next to the event type.
* Fixed truncation of long deduplication keys in the Outbox Viewer.
* Outbox Viewer dates are now displayed in the server's local timezone.



---

# 0.82.0 (2026.04.08)

## New Features
* New playlist check "Track From Latest Release" warns when a playlist contains an older release of a track where a newer version (full album beats single/EP, then newest release date) is available in the artist's catalog.
* Automatic fix replaces outdated track versions with the latest release at the same playlist position.
* New nightly cron job keeps the artist catalog up to date by syncing artist albums distributed over a 14-day cycle.



---

# 0.81.0 (2026.04.07)

## New Features
* Added new playlist type `SINGULARITY` for the "End of the Road" playlist.
* Only one playlist of type `SINGULARITY` can exist per user (analogous to the `ALL` type).
* New playlist check: each artist may have at most one song on a singularity playlist.



---

# 0.80.5 (2026.04.07)

## Bugfixes / Chore
* Fixed sync failure for large playlists when Spotify returns null album information for a track.
* Tracks without album information are now saved and their artists are still synced.



---

# 0.80.4 (2026.04.07)

## Bugfixes / Chore
* Fixed playlist track sync not finding any tracks due to incorrect JSON field mapping.



---

# 0.80.3 (2026.04.06)

## Bugfixes / Chore
* Fixed black text on dark background in tables on health, outbox, and config pages.
* Fixed black text in outbox overlay in the navigation menu.



---

# 0.80.2 (2026.04.06)

## Bugfixes / Chore
* Fixed playlist sync failing with PLAYLIST-003 when syncing large playlists that contain unavailable or deleted tracks.
* Local tracks and tracks without an id are now gracefully skipped during playlist sync.



---

# 0.80.1 (2026.04.06)

## Bugfixes / Chore
* Fixed table text color on dark background.



---

# 0.80.0 (2026.04.06)

## New Features
* Added optional fix support to the playlist checks framework.
* Introduced a Fix button in the Playlist Checks UI to manually remove duplicate tracks from a playlist.
* Duplicate Track IDs fix keeps the first (earliest) occurrence and removes all later duplicates directly in Spotify.



---

# 0.79.3 (2026.04.05)

## Bugfixes / Chore
* Fixed orphaned entries accumulating in the currently playing collection when playback stops.



---

# 0.79.2 (2026.04.05)

## Bugfixes / Chore
* Orphaned currently playing entries are now removed immediately when a different track is detected.
* Pause and resume no longer creates duplicate currently playing entries for the same track.
* Track restarts are correctly detected and stored as a fresh currently playing entry.



---

# 0.79.1 (2026.04.05)

## Bugfixes / Chore
* Dashboard timestamps (recently played, playlist sync times) now display in the server's local timezone instead of UTC.



---

# 0.79.0 (2026.04.05)

## New Features
* Recently played tracks now include a calculated start time.



---

# 0.78.6 (2026.04.03)

## Bugfixes / Chore
* Partial playback entries that duplicate a fully played recently-played entry for the same song are now detected and removed.
* Detection uses approximate song start time (played-at minus duration/played-seconds) with a tolerance of 30 seconds.



---

# 0.78.5 (2026.04.03)

## Bugfixes / Chore
* Fixed missing artist/album image placeholder icons rendering correctly.
* Improved accessible labels on catalog filter, artist filter, and playback histogram links.
* Improved empty state messages in catalog, dashboard, and outbox viewer pages.
* Truncated long deduplication key values in outbox viewer table.



---

# 0.78.3 (2026.04.02)

## Bugfixes / Chore
* Updated outbox library to 0.8.0.
* Updated starters library to version 0.6.1.



---

# 0.78.2 (2026.03.26)

## Bugfixes / Chore
* Improved internal consistency of artist data model.
* Updated architecture documentation to allow CDI and MicroProfile Config annotations in domain-impl service classes.
* Reorganized domain service packages into subdomain hierarchy.



---

# 0.78.1 (2026.03.25)

## Bugfixes / Chore
* Playlists now track the number of tracks they contain.



---

# 0.78.0 (2026.03.25)

## New Features
* Playlist settings now shows a type badge (year, all, unknown) next to each playlist name.
* Playlist check "Duplicate Tracks" renamed to "Duplicate Track IDs".
* New playlist check: year playlists are verified to have all their songs contained in the all playlist.



---

# 0.77.2 (2026.03.25)

## Bugfixes / Chore
* Playlist type ALL is now set automatically when activating a playlist named "All" (case-insensitive).
* Playlist check violations now look up track and artist names from the catalog instead of storing them redundantly.



---

# 0.77.1 (2026.03.25)

## Bugfixes / Chore
* Updated quarkus-outbox dependency to version 0.7.3



---

# 0.77.0 (2026.03.24)

## New Features
* Catalog browser no longer loads all artists on page open; artists are only fetched when a filter is entered.
* Playback settings now loads the first 50 artists per category on page open and fetches additional artists automatically when scrolling to the bottom of each list.



---

# 0.76.3 (2026.03.24)

## Bugfixes / Chore
* Outbox viewer table now uses the same dark table design as playlist checks and playlist sync settings.



---

# 0.76.2 (2026.03.24)

## Bugfixes / Chore
* Outbox viewer now displays text with correct contrast on dark background.
* Outbox viewer columns reordered to: Event Priority, Status, Type, Attempts, Next Retry (UTC), Created (UTC), Deduplication Key, Last Error.
* Outbox viewer deduplication key and last error columns now allow multiline values.



---

# 0.76.1 (2026.03.24)

## Bugfixes / Chore
* Fixed dashboard playback events histogram bars not showing correct heights.



---

# 0.76.0 (2026.03.24)

## New Features
* Added outbox viewer page showing pending tasks per partition with live SSE updates.
* Moved MongoDB Viewer link into the technical dropdown menu (above Atlas).



---

# 0.75.1 (2026.03.24)

## Bugfixes / Chore
* Playback Event Viewer now shows newest events at the top of the list.
* Latest currently playing progress is shown first when viewing today's playback events.
* Grafana dashboard provisioning now retries with exponential backoff (30s, 60s, 120s, 300s) when the Grafana Cloud instance is temporarily unavailable.
* Both metrics and logs dashboards are provisioned on each release.



---

# 0.75.0 (2026.03.24)

## New Features
* Playlist sync now processes one page of tracks at a time, enqueuing catalog sync events per page.
* If a playlist has multiple pages, each page is dispatched as a separate outbox event, preventing long-running tasks and allowing rate limiting to recover gracefully without restarting the full playlist sync.



---

# 0.74.1 (2026.03.24)

## Bugfixes / Chore
* Fixed partial playback duration sometimes showing impossibly large values (e.g. 42039 seconds) by capping the computed duration at the track's actual length.
* Added one-time startup migration to cap any existing partial playback entries with duration exceeding the track's actual length.



---

# 0.74.0 (2026.03.21)

## New Features
* Optimized page rendering time by running independent database queries in parallel for health, config, dashboard, and playlist checks pages.



---

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

