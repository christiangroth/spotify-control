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

Throttling is implemented as a **core feature of `util-outbox`**. The `OutboxTaskDispatcher`
returns a sealed `OutboxTaskResult` that tells the processor exactly what happened. The processor
then acts on that result transparently — the application does not need to manage partition state
directly.

### `OutboxTaskResult` sealed interface (util-outbox)

```kotlin
sealed interface OutboxTaskResult {
    data object Success : OutboxTaskResult
    data class RateLimited(val retryAfter: Duration) : OutboxTaskResult
    data class Failed(val message: String, val cause: Throwable? = null) : OutboxTaskResult
}
```

`OutboxTaskDispatcher.dispatch` returns `OutboxTaskResult` instead of `Either<OutboxError, Unit>`.

### Application mapping (adapter-in-outbox)

`DomainOutboxTaskDispatcher` is responsible for mapping domain-level results to `OutboxTaskResult`.
When `adapter-out-spotify` signals an HTTP 429, the dispatcher extracts the `retryAfter` duration
and returns `OutboxTaskResult.RateLimited(retryAfter)`. Any other failure becomes
`OutboxTaskResult.Failed`. A successful Spotify call returns `OutboxTaskResult.Success`.

The `adapter-out-spotify` adapter reads the `Retry-After` header and converts the raw response to a
domain-level signal (e.g. a dedicated error type or a typed return value). The dispatcher in
`adapter-in-outbox` translates that domain signal to `OutboxTaskResult.RateLimited`.

### Happy path (no 429)

```
OutboxPartitionWorker (spotify)
  └── OutboxProcessor.processNext()
        └── dispatch(task)
              └── adapter-out-spotify → Spotify API (HTTP 2xx)
                    └── returns OutboxTaskResult.Success
                          └── OutboxRepository.complete(task)   (→ outbox_archive)
                                └── loop: claim next task immediately
```

No delay between tasks. The outbox processes Spotify tasks as fast as they complete.

### Rate-limited path (HTTP 429 received)

```
DomainOutboxTaskDispatcher
  receives HTTP 429 signal from adapter-out-spotify
  reads Retry-After: <N> seconds (fallback: 60s if header absent)
  returns OutboxTaskResult.RateLimited(retryAfter = N.seconds)

OutboxProcessor
  receives OutboxTaskResult.RateLimited(retryAfter)
  calls OutboxRepository.pausePartition(partition, reason = "rate_limited", pausedUntil = now + retryAfter)
  calls OutboxRepository.reschedule(task, nextRetryAt = now + retryAfter)  (status → PENDING, attempts unchanged)
  launches resume coroutine: delay(retryAfter) → activatePartition() → workerChannel.send(Unit)

OutboxPartitionWorker
  OutboxProcessor.processNext() returns false (partition status = PAUSED)
  worker suspends on channel.receive()
  resume coroutine fires after retryAfter elapses:
    OutboxRepository.activatePartition(partition)  (status = ACTIVE, clears reason + pausedUntil)
    signals channel → worker resumes
  worker claims next due task and processing continues normally
```

When a 429 is received, **the entire `spotify` partition is paused transparently by the
`OutboxProcessor`** via the core outbox partition-pause feature (see `docs/plans/outbox.md` —
*Partition Pause and Resume*). The application only provides the `OutboxTaskResult.RateLimited`
result; it does not call any partition management methods directly. No further Spotify requests are
dispatched until the resume coroutine reactivates the partition. Tasks enqueued for the partition
during the cooldown are persisted normally but **do not wake the worker**; the resume coroutine is
the sole wakeup mechanism.

---

## New Components

### `OutboxTaskResult` sealed interface (util-outbox)

A sealed interface in `util-outbox` that the `OutboxTaskDispatcher.dispatch` function returns.
Replaces the previous `Either<OutboxError, Unit>` return type. The three variants communicate the
outcome of a single task dispatch to the `OutboxProcessor`:

| Variant | Meaning |
|---------|---------|
| `Success` | Task completed successfully; archive and continue. |
| `RateLimited(retryAfter: Duration)` | External rate limit hit; pause partition and reschedule task. |
| `Failed(message, cause?)` | Task handler error; increment attempts and apply retry backoff. |

### Rate-limit aware processing in `OutboxProcessor` (util-outbox)

`OutboxProcessor.processNext` switches on the returned `OutboxTaskResult`:

- `Success` → `OutboxRepository.complete(task)`.
- `RateLimited(retryAfter)` →
  1. Persists the partition pause to MongoDB:
     `OutboxRepository.pausePartition(partition, reason = "rate_limited", pausedUntil = now + retryAfter)`.
  2. Reschedules the triggering task without incrementing `attempts`:
     `OutboxRepository.reschedule(task, nextRetryAt = now + retryAfter)` — sets `status = PENDING`,
     leaves `attempts` unchanged (the 429 is a transient external constraint, not a handler fault).
  3. Launches a resume coroutine (within the existing coroutine scope):
     ```kotlin
     launch {
         delay(retryAfter)
         outboxRepository.activatePartition(partition)
         workerChannel.send(Unit)
     }
     ```
  4. Returns `false`, causing `OutboxPartitionWorker` to suspend on `channel.receive()`.
- `Failed(message, cause?)` → increments `attempts` and applies the standard `RetryPolicy` backoff.

The resume coroutine is the **only** mechanism that reactivates the partition. There is no
scheduler or polling loop; the coroutine suspends cheaply via `delay` and wakes exactly once when
the cooldown has elapsed.

Partition-pause persistence, enqueueing behaviour during a pause, and startup recovery are all
handled by the core outbox partition-pause feature described in `docs/plans/outbox.md`.

---

## Interaction with the Standard Retry Policy

| `OutboxTaskResult` variant | Increments `attempts`? | Status after handling                               | `nextRetryAt` source         |
|----------------------------|------------------------|-----------------------------------------------------|------------------------------|
| `Failed`                   | Yes                    | `PENDING` (retry) / `FAILED` (max attempts reached) | `RetryPolicy` backoff table  |
| `RateLimited`              | No                     | `PENDING`                                           | `Retry-After` header value   |
| `Success`                  | N/A                    | `DONE` (archived)                                   | N/A                          |

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
