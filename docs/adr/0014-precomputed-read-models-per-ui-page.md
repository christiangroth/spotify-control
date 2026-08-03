# Precomputed Read Models per UI Page

* Status: accepted
* Deciders: Chris
* Date: 2026-08-03

Technical Story: [#867 Slow Queries](https://github.com/christiangroth/spotify-control/issues/867)

## Context and Problem Statement

Issue #867 tracked a series of slow-query reports (HTTP responses of 1.2–5.4s, MongoDB queries of
0.5–4s) on the dashboard, playlist settings, playlist checks, and stats pages. Four consecutive
fixes ([#869](https://github.com/christiangroth/spotify-control/pull/869),
[#870](https://github.com/christiangroth/spotify-control/pull/870),
[#871](https://github.com/christiangroth/spotify-control/pull/871),
[#872](https://github.com/christiangroth/spotify-control/pull/872)) each removed one bottleneck
(parallelizing independent lookups, denormalizing `mainArtistId`, slicing rank entries server-side,
introducing a rolling 30-day aggregate) — but each fix only addressed the specific query that had
just been reported. The root cause recurred: every page still assembled its view by composing
several on-demand queries/aggregations across collections shaped for write-side concerns, not for
what the page actually renders.

How should the application serve UI pages with predictable, low latency without continuing to chase
individual slow queries one report at a time?

## Decision Drivers

* The recurring pattern (issue #867) is architectural, not a series of unrelated bugs: collections
  are shaped for how data is written/synced, not for how pages read it
* MongoDB best practice for this access pattern: shape/aggregate collections for the read side to
  match query needs, rather than computing the shape on every request
* The application already has a durable, at-least-once async mechanism (the persistent outbox,
  [ADR-0007](0007-persistent-outbox-pattern.md)) that can drive this without new infrastructure
* Single-user system ([ADR-0008](0008-single-user-architecture.md)) — eventual consistency between
  a write and the next page view is acceptable; there is no concurrent multi-tenant read audience
* `role-architect.md` currently states "No CQRS, no event sourcing beyond the outbox pattern" —
  this decision is an explicit, scoped exception to that boundary and must update the guideline
  rather than quietly violate it

## Considered Options

1. **Keep fixing individual queries as they are reported** (status quo through #869–#872)
2. **Precomputed read model per UI page**, rebuilt via the existing outbox whenever a relevant
   write happens, read by the page as a single document lookup
3. **Full CQRS with a separate read datastore/message broker**

## Decision Outcome

Chosen option: **"Precomputed read model per UI page"**, because it directly matches the actual
problem (queries shaped for writes, read on demand) without introducing infrastructure beyond what
[ADR-0007](0007-persistent-outbox-pattern.md) already established. Each UI page reads exactly one
precomputed document; the document is rebuilt asynchronously through the existing `domain` outbox
partition whenever a source write occurs, keeping producers (domain writes) and consumers (page
reads) decoupled the same way Spotify-facing outbox events already are.

This replaces `role-architect.md`'s blanket "No CQRS" rule with a scoped one: precomputed
per-page read models, rebuilt exclusively through the existing outbox/starter mechanisms, are
allowed; a separate read datastore, message broker, or general event-sourced write model remains
out of scope.

### Positive Consequences

* Each UI page becomes a single, cheap document lookup — no further per-page query-by-query
  performance chasing
* Reuses existing infrastructure (outbox `domain` partition, `Starter` bootstrap pattern) instead of
  adding a new one
* Read models are independently indexable/shardable from the write-side collections they are
  derived from, without changing the write-side schema

### Negative Consequences

* Introduces a second copy of the same data (write-side collections + read-model document) that must
  be kept in sync by rebuild triggers — a missed trigger means a stale page until the next rebuild
* Adds a new port/adapter/starter per page (naming convention below) — more moving parts than a
  single ad-hoc query, though each part is small and follows an established shape
* Eventual consistency is now a visible, permanent property of these pages rather than an implicit
  side effect of an outbox retry

## Pros and Cons of the Options

### Keep fixing individual queries as they are reported

* Good, because each fix is small and low-risk in isolation
* Bad, because it never converges — issue #867 was reopened four times for the same underlying
  cause
* Bad, because it optimizes today's slow query instead of the page's actual data shape, so the next
  slow query is only a matter of time

### Precomputed read model per UI page

* Good, because it fixes the actual root cause: collections shaped for reads, not just writes
* Good, because it reuses the existing outbox/starter mechanisms instead of new infrastructure
* Good, because eventual consistency is already an accepted trade-off in this single-user system
* Bad, because it requires a scoped exception to the existing "No CQRS" guideline

### Full CQRS with a separate read datastore/message broker

* Good, because it is the "textbook" complete solution
* Bad, because it requires new infrastructure (message broker or second datastore) with no
  domain-justified need — a single-user application has no read-scaling problem to solve
* Bad, because `role-architect.md` explicitly rules out message brokers, and nothing about this
  problem justifies overturning that

## Naming Convention and Directory Structure

* Read-model repository ports live in `domain-api/port/out/readmodel/*RepositoryPort.kt`
* Adapters live in `adapter-out-mongodb`, following the existing `*RepositoryAdapter` pattern, one
  MongoDB collection per read model (`app_<page>_view` naming)
* Rebuild logic lives in the existing domain service already responsible for the underlying data
  (no new service layer); it is invoked wherever that service already handles the outbox event(s)
  that change the underlying data
* First-time backfill for an existing deployment uses the existing `Starter` mechanism
  (`adapter-in-starter`), calling the same inbound port method the rebuild trigger uses — no
  separate migration logic is duplicated

## Links

* [Persistent Outbox for Spotify API Operations](0007-persistent-outbox-pattern.md)
* [Single-User Architecture](0008-single-user-architecture.md)
* Implements the pilot for this ADR: Playlist Checks Tab read model
