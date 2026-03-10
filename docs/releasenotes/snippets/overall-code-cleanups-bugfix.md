* overall-code-cleanups: Removed `/ui` prefix from all web paths and package names.
* overall-code-cleanups: Renamed `DashboardSseService` and `HealthSseService` to `DashboardSseAdapter` and `HealthSseAdapter`.
* overall-code-cleanups: Extracted common SSE connect logic into `connectSse()` helper in `sse-utils.js`.
* overall-code-cleanups: Extracted user placeholder SVG into reusable layout symbol `#icon-user-placeholder`.
* overall-code-cleanups: Removed duplicate first heading from docs pages (heading is now shown as page title only).
* overall-code-cleanups: Renamed MongoDB `CurrentlyPlayingDocument`, `RecentlyPlayedDocument`, and `RecentlyPartialPlayedDocument` to use `Spotify` prefix; renamed collection `recently_partial_played` to `spotify_recently_partial_played`.
* overall-code-cleanups: Replaced dynamic timer in `CurrentlyPlayingFetchJob` with a custom `CurrentlyPlayingSkipPredicate`.
* overall-code-cleanups: Moved `SchedulerInfoAdapter` and `CurrentlyPlayingScheduleState` to new `adapter-out-scheduler` module.
