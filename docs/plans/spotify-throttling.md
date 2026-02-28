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
  receives HTTP 429, reads Retry-After: <N> seconds (fallback: 60s if header absent)
  returns Either.Left(SpotifyRateLimitError(retryAfter = N.seconds))

OutboxProcessor
  receives Either.Left(SpotifyRateLimitError)
  calls OutboxRepository.pausePartition(partition, reason = "rate_limited", pausedUntil = now + retryAfter)
  calls OutboxRepository.fail(task, error, nextRetryAt = now + retryAfter)
  launches resume coroutine: delay(retryAfter) → activatePartition() → workerChannel.send(Unit)

OutboxPartitionWorker
  OutboxProcessor.processNext() returns false (partition status = PAUSED)
  worker suspends on channel.receive()
  resume coroutine fires after retryAfter elapses:
    OutboxRepository.activatePartition(partition)  (status = ACTIVE, clears reason + pausedUntil)
    signals channel → worker resumes
  worker claims next due task and processing continues normally
```

When a 429 is received, **the entire `spotify` partition is paused** by setting `status = PAUSED` in
MongoDB. The `claim` query only returns tasks for partitions where `status = ACTIVE`, preventing any
further Spotify requests during the cooldown. The partition-level pause is a core outbox concept,
not specific to Spotify.

### Enqueueing during partition pause

When a new outbox task arrives for the `spotify` partition while it is `PAUSED`, the task is
persisted to MongoDB as `PENDING` normally — but `OutboxPortAdapter` **does not signal the worker
channel**. The `claim` query would reject the task anyway (partition guard blocks it), and the
resume coroutine is already scheduled to wake the worker at the right moment. Sending a channel
signal while paused would cause the worker to wake early, call `claim`, find nothing claimable
(partition still `PAUSED`), and immediately suspend again — unnecessary work with no benefit.

The resume coroutine is the **sole** mechanism that wakes the worker after a pause. All tasks
enqueued during the cooldown window will be picked up in order once the worker resumes.

---

## New Components

### `OutboxPartition` data model change (util-outbox / MongoDB)

The outbox partition document gains three new fields to support partition-level pausing as a
**core outbox concept**, independent of Spotify:

| Field          | Type        | Default    | Description                                        |
|----------------|-------------|------------|----------------------------------------------------|
| `status`       | `enum`      | `ACTIVE`   | `ACTIVE` — processing normally; `PAUSED` — no tasks claimed until resumed |
| `statusReason` | `String?`   | `null`     | Human-readable reason for the current status (e.g. `"rate_limited"`) |
| `pausedUntil`  | `Instant?`  | `null`     | When the partition will automatically resume; `null` when `ACTIVE` |

The `claim` query gains a partition-level guard: tasks are only returned for partitions where
`status = ACTIVE`. No schema migration is needed for existing documents — absence of `status` is
treated as `ACTIVE`.

### `SpotifyRateLimitError` (domain-api)

A dedicated error sub-type that carries the `retryAfter: Duration` value extracted from the
`Retry-After` response header. Returned by any `adapter-out-spotify` port method when HTTP 429
is received.

```kotlin
data class SpotifyRateLimitError(val retryAfter: Duration) : OutboxError
```

### Rate-limit aware retry in `OutboxProcessor` (util-outbox)

`OutboxProcessor.processNext` inspects the returned `Either.Left` value. When it detects a
`SpotifyRateLimitError` it:

1. Persists the partition pause to MongoDB:
   `OutboxRepository.pausePartition(partition, reason = "rate_limited", pausedUntil = now + retryAfter)`.
2. Reschedules the triggering task:
   `OutboxRepository.fail(task, error, nextRetryAt = now + retryAfter)`.
3. Launches a resume coroutine (within the existing coroutine scope):
   ```kotlin
   launch {
       delay(retryAfter)
       outboxRepository.activatePartition(partition)
       workerChannel.send(Unit)
   }
   ```
4. Returns `false`, causing `OutboxPartitionWorker` to suspend on `channel.receive()`.

The resume coroutine is the **only** mechanism that reactivates the partition. There is no
scheduler or polling loop; the coroutine suspends cheaply via `delay` and wakes exactly once when
the cooldown has elapsed.

The standard `RetryPolicy` (5 s → 10 s → 30 s → 60 s, max 5 attempts) still applies to all
other failures. A 429 does **not** increment the `attempts` counter, because it is not a handler
fault; it is a temporary external constraint.

### Startup recovery in `OutboxPartitionWorker` (util-outbox)

On startup each `OutboxPartitionWorker` reads its partition document from MongoDB. If the
partition is found in `PAUSED` state, the worker applies one of two paths before entering the
normal processing loop:

| Condition                          | Action                                                              |
|------------------------------------|---------------------------------------------------------------------|
| `pausedUntil > now`                | Launch resume coroutine with `delay(pausedUntil - now)`, then suspend on `channel.receive()` |
| `pausedUntil <= now` (or `null`)   | Call `activatePartition()` immediately, then start processing normally |

This ensures the partition self-heals after an application restart without external intervention.

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

1. **Hidden daily bulk limits** – If Spotify silently rejects requests without a 429 (e.g. HTTP
   200 with an empty or error body), a separate detection mechanism is needed. This is out of
   scope for the initial throttling implementation and can be addressed with targeted monitoring
   (unexpected empty response bodies, success rate dashboards).

2. **Observability** – Throttle events (429 received, partition paused, resumed) should emit
   structured log entries and ideally increment a counter exposed via the outbox metrics dashboard.
   This is implementation detail rather than a concept concern.
