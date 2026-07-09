# Further Single-User Simplification Opportunities

[ADR-0008](../adr/0008-single-user-architecture.md) established spotify-control as a strict
single-user application: no allow-list, no per-user fan-out, no `spotifyUserId`-keyed MongoDB
schema. That migration is complete.

This document tracks what is left: places where `UserId` is still threaded around purely as
ceremony — a ceremony from the old multi-user design, not from any actual current need to
disambiguate between users, since at most one `User` can ever exist. None of these are urgent;
they are code-quality follow-ups, not corrections of broken behavior.

**Out of scope / must stay:** the OAuth/session flow genuinely needs a stable identity concept —
the login flow resolves and stores the Spotify user id in the session, and `LoginService` must
keep rejecting a second, different Spotify account from logging in while one user is already
registered. This document is only about removing `UserId`/list-shaped code from places that don't
need it, not about removing the identity concept itself.

## 1. Collapse `UserRepositoryPort` to a true singleton accessor

`UserRepositoryPort` (`domain-api/.../port/out/user/UserRepositoryPort.kt`) still exposes
`findAll(): List<User>` and `findById(UserId): User?`, even though the collection can only ever
hold zero or one document. Callers simulate a single-value lookup with list operations instead of
getting one directly:

* `CurrentUserResolver.userId()` — `userRepository.findAll().firstOrNull()?.spotifyUserId`
* `LoginService.handleCallback` — `val existingUsers = userRepository.findAll(); if
  (existingUsers.isNotEmpty() && existingUsers.none { it.spotifyUserId == userId }) { ... }`
* `OverviewMetrics.activeUserCount()` — `userRepository.findAll().size`, exposed as the
  `app.users.active` gauge (in practice always 0 or 1)

**Proposal:** replace `findAll()`/`findById()` with a single `get(): User?`. `UserRepositoryAdapter`
could then also collapse `app_user` to a single fixed-id document rather than keying by the Spotify
user id, since there is nothing left to distinguish. Update `CurrentUserResolver` to call `get()`
directly, simplify `LoginService`'s "different user" guard to a direct equality check against the
resolved user, and rename/reword the `app.users.active` gauge to reflect that it is now a 0/1
"is a user registered" signal rather than a count.

## 2. Stop rebuilding `UserId` from the security identity in web resources

Several `adapter-in-web` resources reconstruct `UserId(securityIdentity.principal.name)` locally
instead of resolving it once via `CurrentUserResolver`: `PlaybackSettingsResource`,
`PlaylistSettingsResource`, `PlaylistsResource`, `HealthSseResource`. Consolidating on one lookup
path removes duplicated, slightly-differently-shaped identity resolution across resources.

## 3. Drop ceremonial `UserId` parameters/guards in domain services

`PlaybackService`, `DashboardService`, and `PlaylistService` still receive or resolve a `UserId`
in several places purely to satisfy a guard clause (`currentUserResolver.userId() ?: return ...`)
or to tag a Micrometer metric / log line with a value that is now always the same constant. Once
(1) lands, revisit whether these can call `CurrentUserResolver` directly where a value is actually
needed rather than threading `UserId` through method signatures as a parameter.

## 4. Collapse `HealthSseAdapter`'s per-user emitter map

`HealthSseAdapter` still keys its SSE emitters by `UserId` (`emittersByUser`, `notifyAllUsers`,
`emitToUser`), the same shape `DashboardSseAdapter` had before it was collapsed to a single shared
emitter list during the single-user migration. The same simplification applies here: there is only
ever one possible subscriber, so a flat emitter list removes the map and the `UserId` parameter on
`stream()`.

## 5. Drop the `userId` tag from playback metrics and the matching Grafana panels

`PlaybackService.recordFetchSuccess`/`recordEventsIngested` (`PlaybackService.kt:175-190`) tag the
`app.playback.last_success_timestamp` and `app.playback.events_ingested` metrics with `userId`, even
though there is only ever one possible value. That ceremony carries through into the Grafana
dashboards under `monitoring/grafana/`:

* `domain-overview.json` — "Playback Events Ingested Rate (by User)" groups `sum by (userId) (...)`
  and uses `{{userId}}` as the legend.
* `overview.json` — "Time Since Last Currently-Playing Fetch" and "Time Since Last Recently-Played
  Fetch" group `... by (userId)` with a `{{userId}}` legend.
* `overview.json` — "Active Users" plots the `app_users_active` gauge, which per item 1 above is
  really a 0/1 "is a user registered" signal rather than a count.

**Proposal:** once item 1 lands and metrics no longer need a `userId` tag, drop it from the two
`PlaybackService` metrics and simplify the corresponding panels: remove the `by (userId)` grouping
and `{{userId}}` legend (a single series is enough) and drop the "(by User)" qualifier from the
events-ingested panel title. Rename/rework the "Active Users" panel alongside the `app.users.active`
gauge rename from item 1. None of this changes what an operator can observe — it removes a
now-meaningless per-user split from panels that only ever show one series.

## Risk

Low. Each item is a mechanical signature/data-shape simplification following patterns already
applied elsewhere in the codebase during the single-user migration (see `DashboardSseAdapter`,
`DashboardPort` for precedent). The main thing to protect explicitly in review is the OAuth/session
flow (`SpotifyCookieAuthMechanism`, `LoginService`), which still legitimately needs a stable user
identity — it should not be simplified away along with the rest.
