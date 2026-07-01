# Metrics Dashboard Concept

Status: draft / proposal

## Goal

Define what a "good" set of dashboards looks like for spotify-control, so the existing
Grafana dashboards (`monitoring/grafana/`) can be cleaned up and extended in a structured way
instead of growing organically panel-by-panel.

## Current State

Grafana Cloud (Prometheus metrics + Loki logs, see [README.md](../../README.md)) already hosts
two dashboards:

- **`quarkus-metrics.json`** – purely technical: JVM (heap, GC, threads), HTTP server, Mongo
  query durations, Spotify outgoing requests, Outbox (task rates, partition status), Scheduler
  (job duration/rate), Starters.
- **`quarkus-logs.json`** – Loki log explorer (log events per level, free-text search).

Both are technical/infrastructure dashboards. There are **no domain (business) metrics**
instrumented today – the only custom Micrometer instrumentation is infra-level
(`SpotifyHttpMetrics`, `MongoQueryMetrics`, the `playlist.check` timer in
`PlaylistCheckService`). There is no single place that answers "what is the app doing for me
right now?" at a glance.

## Problems with the current setup

- Technical and business concerns are mixed into a single dashboard with no clear grouping –
  hard to tell at a glance whether the *system* is healthy or whether *my data* is up to date.
- No "at a glance" overview: answering "when did the last playback poll succeed?", "is my
  playlist sync up to date?", "are there pending album-upgrade decisions?" currently requires
  reading logs or querying MongoDB directly.
- No domain/business metrics exist to answer those questions even if a panel existed for them.
- Logs and metrics dashboards are unrelated today – jumping from a metrics anomaly to the
  relevant logs means manually re-typing time range and filters in Grafana Explore.

## Proposed Structure: three dashboards, not one

Rather than cramming everything into one dashboard, split by audience/question:

### 1. Overview Dashboard ("what's going on right now")

A small, single-screen dashboard meant to be glanced at, not analyzed. Stat/single-value panels
only, no deep-dive graphs:

| Panel | Source |
|-------|--------|
| Last successful `FetchCurrentlyPlaying` / `FetchRecentlyPlayed` per user | new gauge (see below) |
| Last successful `PlaylistSyncJob` run | new gauge |
| Pending outbox tasks (total + per partition) | existing outbox metrics |
| Errors/exceptions in the last hour | existing Loki log count, panel-linked (see "Logs" below) |
| Active users / playlists tracked | new gauge |
| Pending album-upgrade decisions | new gauge |
| Starters overall status | existing (`Starter Overall Status` panel, reused) |
| App version / uptime | existing (`App Version`, `Process Uptime`, reused) |

This is the dashboard to open first when checking "is everything fine?".

### 2. Technical Metrics Dashboard (cleaned-up `quarkus-metrics.json`)

Keep this dashboard infra-only and organize it into clearly labelled rows (Grafana row panels),
re-using what already exists:

- **JVM** – Heap/Non-Heap Memory, GC Pause Duration, JVM Threads
- **HTTP Server** – Request Rate, Response Time Percentiles, Error Rate (non-2xx)
- **MongoDB** – Query Duration Percentiles, Slow Query Rate
- **Spotify API (Outgoing)** – Request Rate by URL & Status, Duration Percentiles
- **Outbox** – Task Enqueued/Processed Rate, Failed & Rate-Limited Task Rates, Partition Status
- **Scheduler** – Job Execution Rate, Job Duration Percentiles
- **Starters** – Overall Status

Cleanup tasks identified while reviewing the existing JSON (tracked as implementation
follow-ups, not part of this concept):

- Several panels currently sit loose at the dashboard root instead of inside a named row
  (`App Version`, `Application Info`, `Process Uptime`) – group them into a small "Application
  Info" row instead of scattering them.
- Panel titles are inconsistent in scope, e.g. `JVM - GC & Threads` vs. `JVM - Memory` vs. a
  separate top-level `JVM Threads` panel that duplicates information already in the row.

### 3. Business / Domain Metrics Dashboard (new)

This is the actual gap. Proposed panels, grouped by feature area from the
[arc42 building blocks](../arc42/arc42.md#building-block-view):

| Row | Panels |
|-----|--------|
| **Playback Tracking** | Playback events ingested/min (recently-played + partial-play), per user; time since last successful poll per user/job |
| **Playlist Mirror** | Playlists tracked per user; playlists currently out of sync (`syncStatus` ≠ in-sync); time since last successful `PlaylistSyncJob` |
| **Playlist Maintenance** | Duplicates removed (counter); album upgrades applied (counter); pending album-upgrade decisions (gauge) |
| **Catalog Sync** | `app_artist` / `app_track` / `app_album` collection size growth; sync pool backlog size (`app_sync_pool`) |
| **Listening Stats** | Aggregation job runs/duration (already partially covered by Scheduler metrics, surfaced here from a domain angle: "stats last refreshed at …") |

These require new Micrometer instrumentation in the domain layer (counters for "things that
happened" such as duplicates removed/album upgrades applied, gauges for "current state" such as
pending decisions or sync backlog size) – analogous to the existing `playlist.check` timer in
`PlaylistCheckService`. Per [CLAUDE.md logging guidelines](../../CLAUDE.md#logging), these are
metrics, not log lines – per-poll/per-item events stay out of INFO logs and get represented as
Prometheus counters/gauges instead.

Naming convention: `app.<feature>.<event>`, e.g. `app.playlist.duplicates_removed`,
`app.playlist.album_upgrades_applied`, `app.catalog.sync_pool_backlog`, mirroring the existing
`playlist.check` timer naming.

## Connecting Logs and Metrics

Considered two options:

1. **Correlate via exemplars/trace IDs** – technically possible with Grafana + Loki/Prometheus,
   but requires structured log correlation IDs end-to-end and is significant additional
   engineering effort for a small private app with one operator.
2. **Just link the dashboards** – add a Grafana **data link** on key panels (e.g. "Error Rate",
   "Failed Task Rate") that jumps to `quarkus-logs.json` with the same time range and a level
   filter pre-applied, plus a static "View Logs" link in the dashboard header/description of
   each metrics dashboard.

**Recommendation: option 2.** It solves the actual need ("I saw a spike, show me the logs for
that time range") without building correlation infrastructure. Implementation is a few Grafana
dashboard JSON link annotations – no application code changes required.

## Implementation Plan (follow-up issues, not part of this doc)

1. Reorganize `quarkus-metrics.json` into rows as described above (no new metrics needed).
2. Add dashboard cross-links between metrics and logs dashboards (Grafana data links).
3. Instrument new domain counters/gauges (per feature area above) in `domain-impl`.
4. Build the Business/Domain dashboard JSON against the new metrics.
5. Build the Overview dashboard JSON, reusing existing + new panels as single-stat panels.

## Non-Goals

- No new monitoring infrastructure (still Grafana Cloud, Prometheus, Loki — no self-hosted
  components, no tracing/exemplar correlation).
- No in-app dashboard UI – this is exclusively about the Grafana dashboards.
