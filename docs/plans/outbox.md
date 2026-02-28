# Outbox Implementation Concept

## Purpose and Goals

The outbox pattern provides a reliable, persistent task queue that decouples producers from consumers
and ensures that no task is silently lost. In this project the outbox serves two main purposes:

1. **Spotify API calls** – Every write to the Spotify API goes through the outbox, allowing the
   system to honour rate limits, retry on transient failures, and survive application restarts
   without losing pending operations.
2. **Domain async tasks** – Long-running or cascading domain operations (enrichment,
   aggregation, playlist invariant checks) are dispatched via the outbox rather than executed
   inline, keeping request latency low and making each step independently observable.

### Key requirements

* At-least-once delivery: a task is never silently dropped on application restart or failure.
* Ordering within a partition is preserved (FIFO within the same priority level).
* High-priority tasks jump ahead of normal-priority tasks within the same partition.
* Duplicate tasks are silently skipped at enqueue time — no two active tasks with the same deduplication key can coexist in the same partition.
* Partitions are processed independently; a slow Spotify partition does not block the domain
  partition.
* No additional runtime dependencies beyond what the project already uses.

---

## Dependency Analysis

A full outbox can be built on top of the libraries and frameworks already present:

| Need                        | Existing capability                                         |
|-----------------------------|-------------------------------------------------------------|
| Persistent durable store    | MongoDB Atlas via `quarkus-mongodb-panache-kotlin`          |
| Atomic claim / lock-free    | MongoDB `findOneAndUpdate` with status field                |
| Event-driven wakeup         | `kotlinx.coroutines.channels.Channel` (Kotlin coroutines)  |
| Background coroutine        | Quarkus `StartupEvent` + `CoroutineScope(Dispatchers.IO)`  |
| Serialisation               | BSON / Jackson (already on classpath via Quarkus)           |
| Result type for handlers    | Arrow `Either` (already in `domain-api`)                   |
| CDI wiring                  | Quarkus Arc / CDI                                           |

**Conclusion: `kotlinx-coroutines-core` is the only additional dependency needed.**  
Kafka, RabbitMQ, Debezium, and dedicated outbox libraries are all out of scope.

---

## Module Structure

```
util-outbox          – ✅ Implemented – see docs/arc42/outbox.md
adapter-in-outbox    – ✅ Implemented – OutboxPartitionWorker + OutboxStartupRecovery
adapter-out-outbox   – ✅ Implemented – OutboxPortAdapter
adapter-out-spotify  – Spotify API client (no outbox handler responsibility).
domain-impl          – ✅ OutboxHandlerAdapter implemented for FetchRecentlyPlayed and UpdateUserProfiles
domain-api           – ✅ OutboxPort, OutboxHandlerPort, AppOutboxPartition, AppOutboxEvent implemented
```

Future event types (SyncPlaylist, SyncTrack, SyncArtist, EnrichPlaybackEvents, etc.) will be added
to `AppOutboxEvent` and `AppOutboxHandlerPort` as new use cases are implemented. See `docs/arc42/outbox.md`
for the current implementation reference.

---


### Claim

`OutboxRepository.claim(partition)` uses MongoDB `findOneAndUpdate` to atomically:

* Filter: `status = PENDING AND partition = <p> AND (nextRetryAt IS NULL OR nextRetryAt <= now)`
* Sort: `priority DESC, createdAt ASC` (HIGH priority first, then FIFO within each priority level)
* Update: `status → PROCESSING, updatedAt → now`
* Return: the updated document

This atomic operation is safe for a single application instance and remains correct if a second
instance is ever added (because `findOneAndUpdate` is atomic at the document level in MongoDB).

### Dispatch

`OutboxProcessor.processNext(partition, dispatch)` (in `util-outbox`):

1. Claims one task for the given partition.
2. If no task is available, returns `false` (caller will park the thread).
3. If a task was claimed: calls `dispatch(task)` – the function provided by `adapter-in-outbox`.
4. On `Either.Right` (success): calls `OutboxRepository.complete(task)` which inserts into
   `outbox_archive` and deletes from `outbox`.
5. On `Either.Left` (failure): calls `OutboxRepository.fail(task, error, nextRetryAt)` which
   increments `attempts` and sets `status = PENDING` (or `FAILED` if `maxAttempts` is reached).
   For failures that must not count as an attempt (e.g. HTTP 429), the caller uses
   `OutboxRepository.reschedule(task, nextRetryAt)` instead — sets `status = PENDING` without
   touching `attempts`.
6. Returns `true` so the caller loops immediately for the next task.

### Stale task recovery

On application startup, `OutboxStartupRecovery` (`adapter-in-outbox`) resets any task stuck in
`PROCESSING` status back to `PENDING` with `nextRetryAt = now`. This handles the scenario where
a previous instance crashed mid-processing.

---

## Partition Semantics and Scheduling

Partitions are processed by independent `OutboxPartitionWorker` coroutines, one per partition.
Each worker uses a `Channel<Unit>(Channel.CONFLATED)` for event-driven wakeup:

```kotlin
while (isActive) {
    channel.receive()           // suspend until signalled
    do {
        val processed = processor.processNext(partition, dispatch)
    } while (processed && isActive)
    // Queue drained – return to receive() and suspend
}
```

`isActive` is a standard Kotlin coroutines property that reflects the liveness of the coroutine's
`Job`. It becomes `false` when the `CoroutineScope` owning the worker is cancelled, which is how
graceful shutdown is achieved: cancelling the scope (e.g. in a Quarkus `@PreDestroy` lifecycle
callback) propagates cancellation to all partition worker coroutines, causing the `while (isActive)`
guard and any in-progress `channel.receive()` suspension to exit cleanly.

The channel is signalled by `OutboxPortAdapter.enqueue()` immediately after inserting a new
task, and by `OutboxStartupRecovery` once on startup after resetting stale tasks. Using
`Channel.CONFLATED` means multiple rapid enqueues coalesce into a single wakeup signal, and the
worker drains the full queue in a tight loop before suspending again. This means:

* **Zero idle CPU** – the coroutine suspends when there is nothing to do.
* **Zero extra latency** – processing starts as soon as a task is enqueued.
* **No polling fallback needed** – `Channel` delivers signals reliably; the startup signal
  covers the stale-task recovery case without a 30-second timeout.

| Partition | Wakeup mechanism            | Batch approach               | Rationale                                              |
|-----------|------------------------------|------------------------------|--------------------------------------------------------|
| `spotify` | `Channel<Unit>(CONFLATED)`   | One at a time                | One Spotify call at a time; serial processing avoids distributed rate-limit coordination |
| `domain`  | `Channel<Unit>(CONFLATED)`   | Loop drains queue            | CPU-bound enrichment; no artificial delay needed |

A handler failure in the `spotify` partition does not affect `domain` processing because each
partition has its own coroutine and channel.

---

## Partition Pause and Resume

Any partition can be paused by writing `status = PAUSED` to its `outbox_partitions` document. While
paused, the `claim` query returns no tasks for that partition, so the `OutboxPartitionWorker`
suspends on `channel.receive()` without spinning.

### Pausing a partition

`OutboxRepository.pausePartition(partition, reason, pausedUntil)` atomically upserts the partition
document:

```
outbox_partitions.findOneAndUpdate(
    filter  = { _id: partition.key },
    update  = { $set: { status: "PAUSED", statusReason: reason, pausedUntil: pausedUntil } },
    options = { upsert: true }
)
```

After persisting, the caller (e.g. `OutboxProcessor` on a rate-limit error) launches a resume
coroutine within the existing coroutine scope:

```kotlin
launch {
    delay(pausedUntil - now)
    outboxRepository.activatePartition(partition)
    workerChannel.send(Unit)
}
```

The coroutine suspends cheaply via `delay` and fires exactly once — no polling, no scheduler.

### Activating a partition

`OutboxRepository.activatePartition(partition)` clears the pause fields:

```
outbox_partitions.findOneAndUpdate(
    filter  = { _id: partition.key },
    update  = { $set: { status: "ACTIVE" }, $unset: { statusReason: "", pausedUntil: "" } },
    options = { upsert: true }
)
```

### Startup recovery

On startup, `OutboxStartupRecovery` reads each partition document. For any partition found in
`PAUSED` state:

| Condition                        | Action                                                                        |
|----------------------------------|-------------------------------------------------------------------------------|
| `pausedUntil > now`              | Launch resume coroutine with `delay(pausedUntil - now)`, then suspend worker  |
| `pausedUntil <= now` (or `null`) | Call `activatePartition()` immediately, then start processing normally        |

This ensures every partition self-heals after an application restart without external intervention.

---

## Task Priority

Certain event types are time-sensitive and must not be delayed by a backlog of lower-priority
tasks. `PollRecentlyPlayed` is the primary example: Spotify's recently-played history has a fixed
retention window and must be fetched promptly to avoid silently missing playback events.

### Priority field

The `outbox` collection stores a `priority` field (`NORMAL` or `HIGH`, default `NORMAL`). The
`claim` query sorts `priority ASC, createdAt ASC`. The enum names were chosen so that `HIGH`
sorts before `NORMAL` alphabetically (`H` < `N`), which gives the correct semantic order: HIGH
tasks are claimed first, NORMAL tasks after. Within the same priority level FIFO order is preserved.

`OutboxTask` exposes the priority as an `OutboxTaskPriority` field. `OutboxPort.enqueue` gains an
optional `priority` parameter (default `NORMAL`):

```kotlin
interface OutboxPort {
    fun enqueue(
        partition: AppOutboxPartition,
        eventType: AppOutboxEventType,
        payload: Any,
        priority: OutboxTaskPriority = OutboxTaskPriority.NORMAL,
    )
}
```

### Project-specific priority assignments

| Event type              | Priority | Rationale                                                         |
|-------------------------|----------|-------------------------------------------------------------------|
| `PollRecentlyPlayed`    | `HIGH`   | Playback data expires quickly; delays risk missing playback events |
| All other event types   | `NORMAL` | No strict latency requirement                                     |

The caller (domain service via `OutboxPort`) decides the priority at enqueue time. The outbox core
has no knowledge of which event types are high-priority — the decision is encapsulated in the
call site.

---

## Task Deduplication

To prevent redundant work, the outbox automatically suppresses duplicate tasks. If a task with the
same logical identity is already pending or being processed, a second enqueue call for the same
operation is silently discarded.

### Payload deduplication key

The key must uniquely identify the logical operation within its partition. Implementations combine
the event type name with the business entity identifier(s):

```kotlin
// domain-api – examples
data class SyncPlaylistPayload(val playlistId: String) : OutboxPayload {
    override fun deduplicationKey() = "SyncPlaylist:$playlistId"
}

data class SyncTrackPayload(val trackId: String) : OutboxPayload {
    override fun deduplicationKey() = "SyncTrack:$trackId"
}

data class PollRecentlyPlayedPayload(val userId: String) : OutboxPayload {
    override fun deduplicationKey() = "PollRecentlyPlayed:$userId"
}
```

For event types that have no meaningful distinguishing entity (i.e. there should only ever be one
pending instance at a time), the key is the event type name alone:

```kotlin
data class RecomputeAggregationsPayload() : OutboxPayload {
    override fun deduplicationKey() = "RecomputeAggregations"
}
```

### Deduplication check at enqueue time

`MongoOutboxRepository.enqueue` performs a `findOne` before insert:

```
outbox.findOne(
    filter = { partition: partition.key, deduplicationKey: key, status: { $in: ["PENDING", "PROCESSING"] } }
)
```

* **Match found** → return without inserting. The existing task will handle the operation when it
  is claimed.
* **No match** → proceed with the normal insert.

`FAILED` tasks are excluded from the duplicate check so that a manually-retried or re-enqueued
task is never blocked by a previous failure.

For a single-instance deployment this check is safe without further locking. If horizontal scaling
is ever introduced, a partial unique index on `(partition, deduplicationKey)` with status filter
`{status: {$in: ["PENDING", "PROCESSING"]}}` can enforce the constraint at the database level.

### `deduplicationKey` field on `OutboxDocument`

The computed key is stored as the `deduplicationKey` field on every `OutboxDocument` so that
queries and indexes can operate on it without deserialising the JSON payload.

---

## Ordering and Idempotency

**Ordering**: FIFO within a partition is maintained by claiming the task with the earliest
`createdAt`. There is no ordering guarantee across partitions.

**Idempotency**: The system operates at-least-once semantics. On application restart a task in
`PROCESSING` status may be executed again. Handlers are responsible for being idempotent:
* MongoDB upserts (`replaceOne` with upsert) are idempotent by nature.
* Spotify API writes should be preceded by a read to detect already-applied changes.
* Aggregation recomputation is a pure replace operation and is naturally idempotent.

---

## Module Dependency Graph

```
domain-api
    ├── OutboxPort (enqueue interface for domain services)
    ├── OutboxHandlerPort (one method per AppOutboxEventType; implemented by domain-impl)
    ├── OutboxMetricsPort (read-only metrics interface for the dashboard)
    ├── AppOutboxPartition (sealed, extends OutboxPartition from util-outbox)
    └── AppOutboxEventType (sealed, extends OutboxEventType from util-outbox)

util-outbox  ✅ Implemented – see docs/arc42/outbox.md

adapter-in-outbox
    ├── depends on: domain-api, util-outbox
    ├── OutboxPartitionWorker  (coroutine per partition; wakeup via Channel.receive() – no polling)
    ├── OutboxStartupRecovery  (StartupEvent → resets stale PROCESSING tasks + signals each channel)
    └── exhaustive when dispatch (AppOutboxEventType → OutboxHandlerPort method)

adapter-out-outbox
    ├── depends on: domain-api, util-outbox
    └── OutboxPortAdapter      (implements OutboxPort → delegates to OutboxRepository.enqueue + signals Channel)

adapter-out-spotify
    ├── depends on: domain-api
    └── Spotify API client ports (called by domain-impl handler methods, no outbox responsibility)

domain-impl
    ├── depends on: domain-api, adapter-out-spotify
    └── OutboxHandlerImpl      (implements OutboxHandlerPort; all methods for all event types)
```

`adapter-out-mongodb` has no outbox-specific content; it remains exclusively for application
domain data (users, tracks, playlists, events, aggregations).

---

## Integration with `adapter-in-web`

The web adapter does not write to the outbox directly. It calls domain service ports (`UserServicePort`,
etc.), which in turn enqueue tasks via `OutboxPort`. This preserves the hexagonal boundary: the web
adapter has no knowledge of the outbox.

Exception: the dashboard page reads outbox metrics (pending count per partition, recent failures) to
display to the user. This is a read-only query exposed via a dedicated `OutboxMetricsPort` in
`domain-api`, implemented by `util-outbox` (`MongoOutboxRepository`).

---

## Testing Strategy

| Test type              | Location              | Status | Scope                                                                          |
|------------------------|-----------------------|--------|--------------------------------------------------------------------------------|
| Unit – processor logic | `util-outbox`         | ✅ Done | Retry policy application, claim → dispatch → complete/fail cycle with in-memory `OutboxRepository` stub |
| Unit – wakeup service  | `util-outbox`         | ✅ Done | Channel per partition, signal delivery |
| Integration – MongoDB  | `application-quarkus` | ✅ Done | `MongoOutboxRepository` against embedded MongoDB; verifies claim atomicity, archive insertion, and deduplication check |
| Unit – handler logic   | `domain-impl`         | ✅ Done | `OutboxHandlerAdapterTests` covers each method with mock ports |
| Unit – dispatch wiring | `adapter-in-outbox`   | ☐ TODO | Exhaustive `when` routes each `AppOutboxEvent` to the correct `OutboxHandlerPort` method |
| Contract – event types | `application-quarkus` | ☐ TODO | Asserts that each `AppOutboxEvent` has a corresponding method in `OutboxHandlerPort`; fails the build if a new event type is added without updating the port |
| Contract – payload schema | `application-quarkus` | ☐ TODO | Round-trip serialise/deserialise each payload class; detects breaking schema changes |
| Contract – deduplication keys | `application-quarkus` | ☐ TODO | Asserts that every `AppOutboxEvent` returns a non-blank `deduplicationKey()` |

---

## Open Questions

1. **Payload versioning**: Should payloads carry an explicit schema version field from the start,
   or is it sufficient to add one when a breaking change is first needed? Recommendation: add a
   `version: Int = 1` field to every payload class from the outset; migration logic reads the
   version and upgrades in the handler.

2. **Dead-letter visibility**: Should `FAILED` tasks be surfaced in the dashboard immediately, or
   is a separate alert (e.g. Slack message when `status = FAILED` is written) more appropriate?
   Both can be implemented without changing the core design.

3. **Multi-instance deployment**: The current design is safe for a single-instance deployment.
   If horizontal scaling is ever needed, the `findOneAndUpdate` claim is already atomic and
   correct. The stale-task recovery on startup would need a distributed lock or a lease-based
   approach.

4. **util-outbox extraction**: If extracted as an external library in the future, the MongoDB
   persistence layer should be separated (e.g. via a `OutboxRepository` implementation module)
   and the Jackson/BSON serialisation should be replaced with a pluggable `PayloadSerializer`
   interface so callers can bring their own serialisation strategy.
