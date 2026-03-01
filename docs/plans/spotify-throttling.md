# Spotify Request Throttling Concept

## Context

The Spotify Web API enforces two layers of rate limiting:

1. **Short-term (rolling window)** – Spotify returns `HTTP 429 Too Many Requests` with a
   `Retry-After` header (seconds) when a client exceeds an undocumented per-window request quota.
2. **Hidden daily bulk limit** – Aggressive use of write APIs (adding/removing playlist tracks,
   syncing large catalogues) can trigger silent or delayed rejections with no clear signal.

The application currently issues all Spotify API calls via `adapter-out-spotify`. The outbox
pattern routes all Spotify calls through the `spotify` partition of the persistent outbox queue,
processing **one task at a time**. This document records the architectural decisions for how
throttling is layered on top of that architecture.

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
