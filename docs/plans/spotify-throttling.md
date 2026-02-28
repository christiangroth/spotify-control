# Spotify Request Throttling Concept

## Context

The Spotify Web API enforces two layers of rate limiting:

1. **Short-term (rolling window)** – Spotify returns `HTTP 429 Too Many Requests` with a
   `Retry-After` header (seconds) when a client exceeds an undocumented per-window request quota.
2. **Hidden daily bulk limit** – Aggressive use of write APIs (adding/removing playlist tracks,
   syncing large catalogues) can trigger silent or delayed rejections with no clear signal.

The application currently issues all Spotify API calls via `adapter-out-spotify`. The outbox
pattern (designed in `docs/plans/outbox.md`) routes all Spotify calls through the `spotify`
partition of the persistent outbox queue, processing **one task at a time**. This document
addresses how throttling should be layered on top of that architecture.

---

## Key Questions

### 1. Should throttling kick in proactively or only on HTTP 429?

**Decision: React to HTTP 429 only.**

A proactive token-bucket approach (e.g. self-imposed ~50 req/30 s) adds complexity and coupling
to implementation details of Spotify's current rate limits, which are undocumented and subject to
change. Since the outbox already serialises Spotify requests (one at a time), the realistic
request rate under normal operation will be well below any hard limit.

Throttling is therefore triggered **reactively**: when `adapter-out-spotify` receives an HTTP 429,
it extracts the `Retry-After` value and converts this into a backoff signal consumed by the outbox
processor.

Advantages:
* Simple: no token bucket state to manage.
* Self-correcting: the actual Spotify rate limit policy is honoured regardless of how it changes.
* Accurate: the `Retry-After` header is authoritative; self-imposed limits are guesswork.

### 2. Should all requests be routed through the outbox?

**Decision: Yes, all Spotify API calls go through the outbox (existing design).**

The outbox plan already establishes this: every Spotify operation is represented as an
`AppOutboxEventType` in the `spotify` partition. No direct Spotify calls are made from domain
services or the web adapter. The throttling mechanism described here assumes that invariant.

Benefits of this approach relevant to throttling:
* The outbox is a natural back-pressure point: slowing down the processor automatically pauses
  all Spotify traffic without per-call changes.
* Retry-with-delay is already a first-class concept in the outbox (`nextRetryAt` field).
* The task queue absorbs bursts; the processor drains at whatever rate Spotify currently allows.

### 3. Can we leave requests in parallel?

**Decision: No – the `spotify` partition remains strictly serial (one task at a time).**

Keeping requests serial is the simplest correct behaviour and is already the plan for the outbox.
Parallel Spotify requests would require:
* Distributed request counting to stay within rate limits.
* Coordinated back-off when a 429 is received mid-flight.
* More complex retry and ordering logic.

For the expected workload (a single user, moderate sync activity), serial processing is entirely
adequate. If throughput needs increase significantly in the future, concurrency can be revisited
as a separate change.

---

## Throttling Mechanism

### Happy path (no 429)

```
OutboxPartitionWorker (spotify)
  └── OutboxProcessor.processNext()
        └── dispatch(task)
              └── adapter-out-spotify → Spotify API (HTTP 2xx)
                    └── OutboxRepository.complete(task)   (→ outbox_archive)
                          └── loop: claim next task immediately
```

No delay between tasks. The outbox processes Spotify tasks as fast as they complete.

### Rate-limited path (HTTP 429 received)

```
adapter-out-spotify
  receives HTTP 429, reads Retry-After: <N> seconds
  returns Either.Left(SpotifyRateLimitError(retryAfter = N.seconds))

OutboxProcessor
  receives Either.Left(SpotifyRateLimitError)
  calls OutboxRepository.fail(task, error, nextRetryAt = now + retryAfter)

OutboxPartitionWorker
  OutboxProcessor.processNext() returns false (queue appears empty / next task not yet due)
  worker suspends on channel.receive()
  a separate ScheduledRetrySignal job wakes the channel at nextRetryAt
  worker resumes → claim picks up the task (nextRetryAt <= now)
```

The task is **not** consumed; it is reset to `PENDING` with `nextRetryAt` set to
`now + Retry-After`. The existing `claim` query (`nextRetryAt IS NULL OR nextRetryAt <= now`)
already handles this without schema changes.

---

## New Components

### `SpotifyRateLimitError` (domain-api)

A dedicated error sub-type that carries the `retryAfter: Duration` value extracted from the
`Retry-After` response header. Returned by any `adapter-out-spotify` port method when HTTP 429
is received.

```kotlin
data class SpotifyRateLimitError(val retryAfter: Duration) : OutboxError
```

### Rate-limit aware retry in `OutboxProcessor` (util-outbox)

`OutboxProcessor.processNext` inspects the returned `Either.Left` value. When it detects a
`SpotifyRateLimitError` it passes the `retryAfter` duration directly to
`OutboxRepository.fail(task, error, nextRetryAt = now + retryAfter)`, overriding the standard
`RetryPolicy` backoff for that specific attempt. This avoids wasting a retry slot on a delay
that Spotify itself specified.

The standard `RetryPolicy` (5 s → 10 s → 30 s → 60 s, max 5 attempts) still applies to all
other failures. A 429 does **not** increment the `attempts` counter, because it is not a handler
fault; it is a temporary external constraint.

### `ScheduledRetrySignal` (adapter-in-outbox)

A lightweight Quarkus `@Scheduled` job that runs every 10 seconds. It checks whether any task in
the `spotify` partition has `status = PENDING AND nextRetryAt <= now`, and if so, signals the
`spotify` partition channel. This wakes the dormant `OutboxPartitionWorker` without requiring
polling at the `Channel.receive()` level.

Alternatively, the `OutboxProcessor` can use a timed `withTimeout` on `channel.receive()` instead
of a separate scheduled job. Both approaches are valid; the scheduled job is preferred because it
keeps `OutboxPartitionWorker` simpler and the 10-second check interval is negligible overhead.

---

## Interaction with the Standard Retry Policy

| Failure type             | Increments `attempts`? | `nextRetryAt` source         |
|--------------------------|------------------------|------------------------------|
| Handler exception / error | Yes                   | `RetryPolicy` backoff table  |
| HTTP 429 rate limit       | No                    | `Retry-After` header value   |
| Permanent failure (HTTP 4xx except 429) | Yes     | `RetryPolicy` backoff table  |

A task that previously used retry attempts and then hits a 429 will not consume an additional
attempt slot. Once the `Retry-After` window expires the task resumes from its prior attempt count.

---

## Open Questions

1. **Missing `Retry-After` header** – Spotify documentation indicates `Retry-After` is always
   present on a 429, but defensive handling is needed. If the header is absent, fall back to a
   fixed wait of 30 seconds before re-queuing the task.

2. **Cascading 429 across task types** – A large playlist sync may generate many tasks that all
   hit rate limits in sequence. Consider whether a single 429 should pause the entire `spotify`
   partition for the specified duration (simpler) or only the affected task (current design). The
   current design (per-task `nextRetryAt`) is more fine-grained but may result in rapid
   re-claiming of other tasks that also trigger 429s. A partition-level pause (implemented as a
   shared `rateLimitedUntil: Instant?` field checked in `claim`) may be more efficient for bulk
   operations.

3. **Hidden daily bulk limits** – If Spotify silently rejects requests without a 429 (e.g. HTTP
   200 with an empty or error body), a separate detection mechanism is needed. This is out of
   scope for the initial throttling implementation and can be addressed with targeted monitoring
   (unexpected empty response bodies, success rate dashboards).

4. **Observability** – Throttle events (429 received, partition paused, resumed) should emit
   structured log entries and ideally increment a counter exposed via the outbox metrics dashboard.
   This is implementation detail rather than a concept concern.
