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
util-outbox          – Core logic and MongoDB persistence. Quarkus dependency only.
adapter-in-outbox    – Drives domain processing: event-driven wakeup, exhaustive dispatch, startup recovery.
adapter-out-outbox   – Write side: implements OutboxPort so domain services can enqueue tasks.
adapter-out-spotify  – Spotify API client (no outbox handler responsibility).
domain-impl          – Implements OutboxHandlerPort with one method per event type (all partitions).
domain-api           – Defines OutboxPort, OutboxHandlerPort, and the project's sealed event types.
```

### `util-outbox`

Kotlin module with a Quarkus dependency for MongoDB Panache access (both already used by the project — no new dependencies introduced). Contains:

* `OutboxPartition` – plain (non-sealed) interface with a `key: String` property.
* `OutboxEventType` – plain (non-sealed) interface with a `key: String` property.
* `OutboxTask` – data class (id, partition, eventType, payload as String, status, attempts,
  createdAt, updatedAt, nextRetryAt).
* `OutboxTaskStatus` – enum: `PENDING`, `PROCESSING`, `DONE`, `FAILED`.
* `OutboxPartitionStatus` – enum: `ACTIVE`, `PAUSED`.
* `OutboxPartitionDocument` – Panache entity mapped to the `outbox_partitions` collection; stores
  `status`, `statusReason`, and `pausedUntil` per partition key.
* `OutboxRepository` – interface; provides `claim`, `complete`, `fail`, `reschedule`, `enqueue`,
  `pausePartition`, `activatePartition`, `findPartition`.
* `OutboxDocument` – Panache entity mapped to the `outbox` collection.
* `OutboxArchiveDocument` – Panache entity mapped to the `outbox_archive` collection.
* `MongoOutboxRepository` – `@ApplicationScoped` implements `OutboxRepository` using
  `OutboxDocument` and `OutboxArchiveDocument`.
* `OutboxProcessor` – orchestrates claim → dispatch → complete/fail/retry for one partition.
  Receives a `dispatch: (OutboxTask) -> Either<OutboxError, Unit>` function; has no knowledge
  of specific event types or payload classes.
* `RetryPolicy` – data class (maxAttempts, backoff list of Durations); injected into
  `OutboxProcessor`.

Persistence lives here alongside the core logic, sacrificing framework-independence for
simplicity. Should extraction ever be needed, the persistence layer can be separated at that
point.

### `adapter-in-outbox`

Inbound adapter – drives the domain by consuming the outbox. Depends on `util-outbox` and
`domain-api`. Contains:

* `OutboxPartitionWorker` – one per partition; runs as a coroutine launched at `StartupEvent`.
  The coroutine loops: `channel.receive()` to wait for a wakeup signal, then calls
  `OutboxProcessor.processNext(partition, dispatch)` in a loop until the queue is drained.
  No fixed polling timeout is needed – the `Channel` provides reliable signal delivery.
* `OutboxStartupRecovery` – bean that resets tasks stuck in `PROCESSING` status back to
  `PENDING` on application start (handles the previous-crash scenario), then signals each
  partition channel so workers wake up and process any recovered tasks.
* Exhaustive `when` dispatch: the `dispatch` function passed to `OutboxProcessor` deserialises
  the raw JSON payload and calls the appropriate method on `OutboxHandlerPort` (from
  `domain-api`) using a Kotlin exhaustive `when` on the sealed `AppOutboxEventType`. The
  compiler enforces that every event type has a handler.

The `Channel<Unit>(Channel.CONFLATED)` per partition is signalled by `OutboxPortAdapter`
immediately after a task is enqueued, so processing begins without any fixed delay. Using
`Channel.CONFLATED` means multiple rapid enqueues coalesce into a single wakeup, and the
worker drains the full queue before returning to `receive()`.

This module is an inbound adapter because it is the entry point that triggers domain work –
analogous to a web request handler or a Kafka consumer.

### `adapter-out-outbox`

Outbound adapter – the write side. Depends on `domain-api` and `util-outbox`. Contains:

* `OutboxPortAdapter` – `@ApplicationScoped` bean; implements `OutboxPort` by delegating to
  `OutboxRepository.enqueue`. After the insert it signals the per-partition `Channel<Unit>` in
  `OutboxWakeupService` so the coroutine worker wakes up immediately. This is what domain
  services call to enqueue a task.

### `adapter-out-spotify`

Provides the Spotify API client implementation (outbound HTTP calls). It has no outbox handler
responsibility — it exposes ports that `domain-impl` handlers call to execute Spotify operations.

### `domain-impl`

Implements `OutboxHandlerPort` (from `domain-api`) with one method per event type across both
partitions:

* Methods for `AppOutboxPartition.Spotify` event types call `adapter-out-spotify` ports to
  perform the actual Spotify API operations (sync playlists, push edits, etc.).
* Methods for `AppOutboxPartition.Domain` event types execute enrichment, aggregation, and
  invariant logic entirely within the domain layer.

### `domain-api`

Defines `OutboxPort`, `OutboxHandlerPort`, and the project-specific sealed event types:

```kotlin
interface OutboxPort {
    fun enqueue(partition: AppOutboxPartition, eventType: AppOutboxEventType, payload: Any)
}

// One method per AppOutboxEventType; exhaustive when in adapter-in-outbox guarantees
// every event type is covered at compile time.
interface OutboxHandlerPort {
    fun handleSyncPlaylist(payload: SyncPlaylistPayload): Either<OutboxError, Unit>
    fun handleSyncTrack(payload: SyncTrackPayload): Either<OutboxError, Unit>
    fun handleSyncArtist(payload: SyncArtistPayload): Either<OutboxError, Unit>
    fun handlePushPlaylistEdit(payload: PushPlaylistEditPayload): Either<OutboxError, Unit>
    fun handlePollRecentlyPlayed(payload: PollRecentlyPlayedPayload): Either<OutboxError, Unit>
    fun handleEnrichPlaybackEvents(payload: EnrichPlaybackEventsPayload): Either<OutboxError, Unit>
    fun handleRecomputeAggregations(payload: RecomputeAggregationsPayload): Either<OutboxError, Unit>
    fun handleApplyGenreOverride(payload: ApplyGenreOverridePayload): Either<OutboxError, Unit>
    fun handleSyncPlaylistInvariant(payload: SyncPlaylistInvariantPayload): Either<OutboxError, Unit>
    fun handleCheckAlbumUpgrades(payload: CheckAlbumUpgradesPayload): Either<OutboxError, Unit>
    fun handleApplyAlbumUpgrade(payload: ApplyAlbumUpgradePayload): Either<OutboxError, Unit>
}
```

Domain services depend only on `OutboxPort` and the sealed types in `domain-api`; they never
reference `MongoOutboxRepository` or other `util-outbox` internals directly.

---

## Data Model

### `outbox_partitions` collection

| Field           | Type      | Default    | Description                                              |
|-----------------|-----------|------------|----------------------------------------------------------|
| `_id`           | String    | –          | Partition key (e.g. `"spotify"`, `"domain"`)             |
| `status`        | String    | `ACTIVE`   | `ACTIVE` — processing normally; `PAUSED` — no tasks claimed until resumed |
| `statusReason`  | String?   | `null`     | Human-readable reason for the current status (e.g. `"rate_limited"`) |
| `pausedUntil`   | Instant?  | `null`     | When the partition will automatically resume; `null` when `ACTIVE` |

Documents are created lazily (on first pause or explicit registration). An absent document is
treated as `ACTIVE` so existing deployments require no migration. The `claim` query joins against
this collection and only returns tasks for partitions where `status = ACTIVE`.

### `outbox` collection

| Field          | Type      | Description                                                   |
|----------------|-----------|---------------------------------------------------------------|
| `_id`          | String    | UUID v4                                                       |
| `partition`    | String    | e.g. `"spotify"`, `"domain"`                                 |
| `eventType`    | String    | e.g. `"PollRecentlyPlayed"`, `"EnrichPlaybackEvents"`        |
| `payload`      | String    | JSON-serialised payload                                       |
| `status`       | String    | `PENDING` / `PROCESSING` / `FAILED`                          |
| `attempts`     | Int       | Number of processing attempts so far                          |
| `createdAt`    | Instant   | When the task was enqueued                                    |
| `updatedAt`    | Instant   | Last status change                                            |
| `nextRetryAt`  | Instant?  | Earliest time for the next attempt (null = immediately)       |
| `priority`     | String    | `NORMAL` (default) or `HIGH` — affects claim ordering         |
| `lastError`    | String?   | Error message or stack trace excerpt from the last failure    |

### `outbox_archive` collection

Same structure as `outbox` plus:

| Field          | Type      | Description                   |
|----------------|-----------|-------------------------------|
| `completedAt`  | Instant   | When processing succeeded      |

Successful tasks are inserted into `outbox_archive` and deleted from `outbox` in a single
operation. The archive serves as a persistent audit log and a source of metrics.

---

## Type-Safe Event API

`util-outbox` defines plain (non-sealed) interfaces so the module has no knowledge of the
concrete partitions or event types used by any specific application:

```kotlin
// util-outbox – open extension points
interface OutboxPartition {
    val key: String
}

interface OutboxEventType {
    val key: String
}
```

`domain-api` extends these with project-specific sealed sub-interfaces, giving exhaustive
`when` handling across the whole codebase:

```kotlin
// domain-api – project-specific, sealed
sealed interface AppOutboxPartition : OutboxPartition {
    object Spotify : AppOutboxPartition { override val key = "spotify" }
    object Domain  : AppOutboxPartition { override val key = "domain"  }
}

sealed interface AppOutboxEventType : OutboxEventType {
    // Spotify partition
    object SyncPlaylist          : AppOutboxEventType { override val key = "SyncPlaylist" }
    object SyncTrack             : AppOutboxEventType { override val key = "SyncTrack" }
    object SyncArtist            : AppOutboxEventType { override val key = "SyncArtist" }
    object PushPlaylistEdit      : AppOutboxEventType { override val key = "PushPlaylistEdit" }
    object PollRecentlyPlayed    : AppOutboxEventType { override val key = "PollRecentlyPlayed" }
    // Domain partition
    object EnrichPlaybackEvents  : AppOutboxEventType { override val key = "EnrichPlaybackEvents" }
    object RecomputeAggregations : AppOutboxEventType { override val key = "RecomputeAggregations" }
    object ApplyGenreOverride    : AppOutboxEventType { override val key = "ApplyGenreOverride" }
    object SyncPlaylistInvariant : AppOutboxEventType { override val key = "SyncPlaylistInvariant" }
    object CheckAlbumUpgrades    : AppOutboxEventType { override val key = "CheckAlbumUpgrades" }
    object ApplyAlbumUpgrade     : AppOutboxEventType { override val key = "ApplyAlbumUpgrade" }
}
```

Each handler is typed against a specific payload class and uses the base interfaces so it can
live in `util-outbox`:

```kotlin
// util-outbox – dispatch function received from adapter-in-outbox
// OutboxProcessor.processNext calls it without knowing the concrete type
fun processNext(
    partition: OutboxPartition,
    dispatch: (OutboxTask) -> Either<OutboxError, Unit>,
): Boolean
```

`adapter-in-outbox` builds the `dispatch` lambda using an exhaustive `when` on the sealed
`AppOutboxEventType`, ensuring at compile time that every event type has a handler:

```kotlin
// adapter-in-outbox
val dispatch: (OutboxTask) -> Either<OutboxError, Unit> = { task ->
    val payload = task.payload  // raw JSON String
    when (AppOutboxEventType.fromKey(task.eventType)) {
        AppOutboxEventType.SyncPlaylist          -> handlerPort.handleSyncPlaylist(deserialise(payload))
        AppOutboxEventType.SyncTrack             -> handlerPort.handleSyncTrack(deserialise(payload))
        AppOutboxEventType.SyncArtist            -> handlerPort.handleSyncArtist(deserialise(payload))
        AppOutboxEventType.PushPlaylistEdit      -> handlerPort.handlePushPlaylistEdit(deserialise(payload))
        AppOutboxEventType.PollRecentlyPlayed    -> handlerPort.handlePollRecentlyPlayed(deserialise(payload))
        AppOutboxEventType.EnrichPlaybackEvents  -> handlerPort.handleEnrichPlaybackEvents(deserialise(payload))
        AppOutboxEventType.RecomputeAggregations -> handlerPort.handleRecomputeAggregations(deserialise(payload))
        AppOutboxEventType.ApplyGenreOverride    -> handlerPort.handleApplyGenreOverride(deserialise(payload))
        AppOutboxEventType.SyncPlaylistInvariant -> handlerPort.handleSyncPlaylistInvariant(deserialise(payload))
        AppOutboxEventType.CheckAlbumUpgrades    -> handlerPort.handleCheckAlbumUpgrades(deserialise(payload))
        AppOutboxEventType.ApplyAlbumUpgrade     -> handlerPort.handleApplyAlbumUpgrade(deserialise(payload))
        // Sealed when – compiler error if a new AppOutboxEventType is added without a branch
    }
}
```

---

## Enqueuing Tasks (Write Path)

1. A domain service calls `OutboxPort.enqueue(partition, eventType, payload)`.
2. `OutboxPortAdapter` (in `adapter-out-outbox`) serialises the payload to JSON and delegates to
   `OutboxRepository.enqueue(...)`.
3. `MongoOutboxRepository.enqueue` (in `util-outbox`) inserts a new `OutboxDocument` with
   `status = PENDING`, `attempts = 0`, `nextRetryAt = null`.
4. After the insert, `OutboxPortAdapter` checks the partition's current `status`.
   * If `ACTIVE`: signals the per-partition `Channel<Unit>` so the worker wakes immediately.
   * If `PAUSED`: **no signal is sent**. The `claim` query would reject any task from a paused
     partition anyway, and the resume coroutine (already running) will wake the worker at the
     right time. Sending a premature signal would cause the worker to wake, call `claim`, find
     nothing claimable, and immediately suspend again — unnecessary work with no benefit.

Enqueueing is a single MongoDB insert – it does not need to participate in a multi-document
transaction because the outbox is append-only at write time.

---

## Processing Tasks (Read/Execute Path)

Processing is driven by `OutboxPartitionWorker` threads in `adapter-in-outbox` (one per
partition). Each worker loops continuously on a background thread, sleeping only when the
outbox is empty (see Partition Semantics and Scheduling).

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

## Retry and Backoff Strategy

Configured via `RetryPolicy` (injected, overridable per partition):

| Attempt | Delay before next retry                                 |
|---------|---------------------------------------------------------|
| 1 → 2   | 5 seconds                                              |
| 2 → 3   | 10 seconds                                             |
| 3 → 4   | 30 seconds                                             |
| 4 → 5   | 60 seconds                                             |
| ≥ 5     | task status set to `FAILED` (no more automatic retries) |

Default `maxAttempts = 5` (one initial attempt plus four retries). On reaching `maxAttempts`, the task remains in the `outbox` collection
with `status = FAILED`. It is visible in the dashboard and can be retried manually by resetting its
status to `PENDING` via an admin action (future work) or direct MongoDB operation.

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
`claim` query sorts `priority DESC, createdAt ASC`, so any `HIGH` priority task is always claimed
before `NORMAL` tasks regardless of enqueue order. Within the same priority level FIFO order is
preserved.

The `OutboxTaskPriority` enum is defined in `util-outbox`:

```kotlin
enum class OutboxTaskPriority { NORMAL, HIGH }
```

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

util-outbox
    ├── OutboxPartition (plain interface – open extension point)
    ├── OutboxEventType (plain interface – open extension point)
    ├── OutboxPartitionStatus (enum: ACTIVE, PAUSED)
    ├── OutboxTaskPriority (enum: NORMAL, HIGH)
    ├── OutboxTask, OutboxTaskStatus
    ├── OutboxRepository (interface; claim, complete, fail, reschedule, enqueue, pausePartition, activatePartition, findPartition)
    ├── MongoOutboxRepository (implements OutboxRepository)
    ├── OutboxDocument, OutboxArchiveDocument (Panache entities; outbox / outbox_archive collections)
    ├── OutboxPartitionDocument (Panache entity; outbox_partitions collection)
    ├── OutboxProcessor (claim/complete/fail lifecycle; dispatch is a passed-in function using Result<Unit>)
    ├── RetryPolicy
    └── OutboxWakeupService (Channel<Unit>(CONFLATED) per partition; signal() called by adapter-out-outbox)

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

| Test type              | Location              | Scope                                                                          |
|------------------------|-----------------------|--------------------------------------------------------------------------------|
| Unit – processor logic | `util-outbox`         | Retry policy application, claim → dispatch → complete/fail cycle with in-memory `OutboxRepository` stub |
| Unit – handler logic   | `domain-impl`         | Each `OutboxHandlerPort` method in isolation with a mock Spotify/domain port   |
| Unit – dispatch wiring | `adapter-in-outbox`   | Exhaustive `when` routes each `AppOutboxEventType` to the correct `OutboxHandlerPort` method |
| Integration – MongoDB  | `application-quarkus` | `MongoOutboxRepository` against embedded MongoDB; verifies claim atomicity, archive insertion |
| Contract – event types | `application-quarkus` | Asserts that each `AppOutboxEventType` has a corresponding method in `OutboxHandlerPort`; fails the build if a new event type is added without updating the port |
| Contract – payload schema | `application-quarkus` | Round-trip serialise/deserialise each payload class; detects breaking schema changes |

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
