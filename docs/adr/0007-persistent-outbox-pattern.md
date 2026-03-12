# Persistent Outbox for Spotify API Operations

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

The application needs to make many Spotify API calls (recently played polling, playlist sync,
catalog enrichment). These calls are subject to strict rate limits, occasional 429 responses, and
a hidden 24h bulk limit. Direct API calls from REST handlers or scheduler jobs would be hard to
throttle, retry, and deduplicate reliably. Any failure would require re-triggering from the
scheduler, leading to data loss or duplicate work.

How should the application reliably dispatch Spotify API calls and internal domain events with
rate limit resilience, at-least-once delivery, and deduplication?

## Decision Drivers

* Spotify rate limits require per-partition throttling and backoff
* At-least-once delivery – no events must be lost on application restart
* Deduplication – the same entity must not be enriched multiple times concurrently
* Domain logic must not depend directly on HTTP or Spotify concerns
* The solution must fit within the existing hexagonal architecture

## Considered Options

1. **Direct API calls from scheduler jobs / domain services**
2. **In-memory queue (CDI events + executor)**
3. **Persistent outbox pattern (MongoDB-backed task queue)**

## Decision Outcome

Chosen option: **"Persistent outbox pattern"**, because it provides at-least-once delivery
across restarts, per-partition rate limit handling (pause/resume on 429), and type-safe
deduplication via outbox event keys — all without coupling the domain to Spotify HTTP concerns.

The outbox is provided as the external library `de.chrgroth.quarkus.outbox`
([christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox)).

### Positive Consequences

* Spotify API calls are fully decoupled from domain logic; the domain only writes events.
* Partition-level throttling (10s for `to-spotify`) and pause-on-rate-limit prevent bulk limit breaches.
* Outbox tasks survive application restarts; at-least-once delivery is guaranteed.
* Deduplication keys prevent duplicate Spotify API calls for the same entity.
* The `to-spotify-playback` partition bypasses throttling for time-sensitive playback data.

### Negative Consequences

* Adds complexity: three partitions, event serialization, and an outbox task dispatcher.
* Adds an external dependency (`de.chrgroth.quarkus.outbox`).
* Debugging requires inspecting the `outbox` and `outbox_archive` MongoDB collections.

## Pros and Cons of the Options

### Direct API calls from scheduler jobs / domain services

* Good, because simple – no extra infrastructure.
* Bad, because rate limits are not handled; a single throttled call blocks the scheduler thread.
* Bad, because no retry on failure; missing data requires manual re-trigger.
* Bad, because violates hexagonal architecture (domain would depend on Spotify HTTP).

### In-memory queue (CDI events + executor)

* Good, because no external dependency.
* Good, because CDI events are already used for internal triggers (SSE notifications).
* Bad, because in-memory queue is lost on restart; at-least-once delivery is not guaranteed.
* Bad, because no built-in deduplication or rate limiting.

### Persistent outbox pattern

* Good, because at-least-once delivery across restarts.
* Good, because partition-level throttling and pause-on-rate-limit.
* Good, because type-safe deduplication via outbox event keys.
* Bad, because adds external dependency and operational complexity.

## Links

* [Hexagonal architecture ADR](0002-backend-hexagonal-architecture.md)
* [quarkus-outbox library](https://github.com/christiangroth/quarkus-outbox)
