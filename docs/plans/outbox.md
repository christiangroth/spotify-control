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
* Ordering within a partition is preserved (FIFO).
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
| Scheduled polling           | Quarkus `@Scheduled` (MicroProfile)                        |
| Serialisation               | BSON / Jackson (already on classpath via Quarkus)           |
| Result type for handlers    | Arrow `Either` (already in `domain-api`)                   |
| CDI wiring for handlers     | Quarkus Arc / CDI                                           |

**Conclusion: No additional dependencies are needed.**  
Kafka, RabbitMQ, Debezium, and dedicated outbox libraries are all out of scope.

---

## Module Structure

```
util-outbox          – Core logic and MongoDB persistence. Quarkus dependency only.
adapter-in-outbox    – Drives domain processing: @Scheduled polling, startup recovery.
adapter-out-outbox   – Write side: implements OutboxPort so domain services can enqueue tasks.
adapter-out-spotify  – Registers handlers for the spotify partition.
domain-impl          – Registers handlers for the domain partition.
domain-api           – Defines OutboxPort (enqueue only) and the project's sealed event types.
```

### `util-outbox`

Kotlin module with a Quarkus dependency for MongoDB Panache access (both already used by the project — no new dependencies introduced). Contains:

* `OutboxPartition` – plain (non-sealed) interface with a `key: String` property.
* `OutboxEventType` – plain (non-sealed) interface with a `key: String` property.
* `OutboxTask` – data class (id, partition, eventType, payload as String, status, attempts,
  createdAt, updatedAt, nextRetryAt).
* `OutboxTaskStatus` – enum: `PENDING`, `PROCESSING`, `DONE`, `FAILED`.
* `OutboxRepository` – interface; provides `claim`, `complete`, `fail`, `enqueue`.
* `OutboxDocument` – Panache entity mapped to the `outbox` collection.
* `OutboxArchiveDocument` – Panache entity mapped to the `outbox_archive` collection.
* `MongoOutboxRepository` – `@ApplicationScoped` implements `OutboxRepository` using
  `OutboxDocument` and `OutboxArchiveDocument`.
* `OutboxTaskHandler<P>` – interface; each handler declares the partition and event type it
  handles and receives a typed payload.
* `OutboxProcessor` – orchestrates claim → deserialise → dispatch → complete/fail/retry for
  one partition per `processBatch(partition, batchSize)` call.
* `RetryPolicy` – data class (maxAttempts, backoff list of Durations); injected into
  `OutboxProcessor`.

Persistence lives here alongside the core logic, sacrificing framework-independence for
simplicity. Should extraction ever be needed, the persistence layer can be separated at that
point.

### `adapter-in-outbox`

Inbound adapter – drives the domain by consuming the outbox. Depends on `util-outbox` and
`domain-api`. Contains:

* `OutboxScheduler` – `@ApplicationScoped` bean with `@Scheduled` methods, one per partition,
  at different intervals. Each method calls `OutboxProcessor.processBatch(partition, ...)`.
* `OutboxStartupRecovery` – `@Startup` bean that resets tasks stuck in `PROCESSING` status
  back to `PENDING` on application start (handles the previous-crash scenario).
* CDI producer that wires all registered `OutboxTaskHandler` beans into the `OutboxProcessor`.

This module is an inbound adapter because it is the entry point that triggers domain work –
analogous to a web request handler or a Kafka consumer.

### `adapter-out-outbox`

Outbound adapter – the write side. Depends on `domain-api` and `util-outbox`. Contains:

* `OutboxPortAdapter` – `@ApplicationScoped` bean; implements `OutboxPort` by delegating to
  `OutboxRepository.enqueue`. This is what domain services call to enqueue a task.

### `adapter-out-spotify`

Implements `OutboxTaskHandler<P>` for each event type in the `spotify` partition.
Handlers are CDI beans discovered automatically by `adapter-in-outbox`.

### `domain-impl`

Implements `OutboxTaskHandler<P>` for each event type in the `domain` partition.

### `domain-api`

Defines `OutboxPort` and the project-specific sealed event types:

```kotlin
interface OutboxPort {
    fun enqueue(partition: AppOutboxPartition, eventType: AppOutboxEventType, payload: Any)
}
```

Domain services depend only on `OutboxPort` and the sealed types in `domain-api`; they never
reference `MongoOutboxRepository` or other `util-outbox` internals directly.

---

## Data Model

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
// util-outbox
interface OutboxTaskHandler<P : Any> {
    val partition: OutboxPartition
    val eventType: OutboxEventType
    val payloadClass: KClass<P>
    // Synchronous blocking call; Quarkus runs handlers on managed thread pool workers,
    // so coroutines/suspend are not needed and would add a Kotlin coroutines dependency.
    fun handle(payload: P): Either<OutboxError, Unit>
}
```

---

## Enqueuing Tasks (Write Path)

1. A domain service calls `OutboxPort.enqueue(partition, eventType, payload)`.
2. `OutboxPortAdapter` (in `adapter-out-outbox`) serialises the payload to JSON and delegates to
   `OutboxRepository.enqueue(...)`.
3. `MongoOutboxRepository.enqueue` (in `util-outbox`) inserts a new `OutboxDocument` with
   `status = PENDING`, `attempts = 0`, `nextRetryAt = null`.

Enqueueing is a single MongoDB insert – it does not need to participate in a multi-document
transaction because the outbox is append-only at write time.

---

## Processing Tasks (Read/Execute Path)

Processing is driven by `@Scheduled` jobs in `OutboxScheduler` (`adapter-in-outbox`).

### Claim

`OutboxRepository.claim(partition)` uses MongoDB `findOneAndUpdate` to atomically:

* Filter: `status = PENDING AND partition = <p> AND (nextRetryAt IS NULL OR nextRetryAt <= now)`
* Sort: `createdAt ASC` (FIFO within partition)
* Update: `status → PROCESSING, updatedAt → now`
* Return: the updated document

This atomic operation is safe for a single application instance and remains correct if a second
instance is ever added (because `findOneAndUpdate` is atomic at the document level in MongoDB).

### Dispatch

`OutboxProcessor.processBatch(partition, batchSize)` (in `util-outbox`):

1. Claims up to `batchSize` tasks for the given partition in a loop.
2. For each claimed task: finds the matching handler by `eventType`, deserialises the payload,
   calls `handler.handle(payload)`.
3. On `Either.Right` (success): calls `OutboxRepository.complete(task)` which inserts into
   `outbox_archive` and deletes from `outbox`.
4. On `Either.Left` (failure): calls `OutboxRepository.fail(task, error, nextRetryAt)`.

### Stale task recovery

On application startup, `OutboxStartupRecovery` (`adapter-in-outbox`) resets any task stuck in
`PROCESSING` status back to `PENDING` with `nextRetryAt = now`. This handles the scenario where
a previous instance crashed mid-processing.

---

## Retry and Backoff Strategy

Configured via `RetryPolicy` (injected, overridable per partition):

| Attempt | Delay before next retry                                 |
|---------|---------------------------------------------------------|
| 1 → 2   | 1 minute                                               |
| 2 → 3   | 5 minutes                                              |
| 3 → 4   | 30 minutes                                             |
| ≥ 4     | task status set to `FAILED` (no more automatic retries) |

Default `maxAttempts = 4` (one initial attempt plus three retries). On reaching `maxAttempts`, the task remains in the `outbox` collection
with `status = FAILED`. It is visible in the dashboard and can be retried manually by resetting its
status to `PENDING` via an admin action (future work) or direct MongoDB operation.

---

## Partition Semantics and Scheduling

| Partition | Poll interval | Batch size | Rationale                                              |
|-----------|--------------|------------|--------------------------------------------------------|
| `spotify` | 2 seconds    | 1          | One Spotify call at a time; token bucket enforces ~50 req/30 s |
| `domain`  | 5 seconds    | 5          | CPU-bound enrichment; batching reduces scheduler overhead |

Partitions are processed in independent `@Scheduled` methods with independent error handling.
A handler failure in the `spotify` partition does not affect `domain` processing.

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
    ├── OutboxMetricsPort (read-only metrics interface for the dashboard)
    ├── AppOutboxPartition (sealed, extends OutboxPartition from util-outbox)
    └── AppOutboxEventType (sealed, extends OutboxEventType from util-outbox)

util-outbox
    ├── OutboxPartition (plain interface – open extension point)
    ├── OutboxEventType (plain interface – open extension point)
    ├── OutboxTask, OutboxTaskStatus
    ├── OutboxRepository (interface)
    ├── MongoOutboxRepository (implements OutboxRepository)
    ├── OutboxDocument, OutboxArchiveDocument (Panache entities)
    ├── OutboxTaskHandler<P> (interface)
    ├── OutboxProcessor (core dispatch logic)
    └── RetryPolicy

adapter-in-outbox
    ├── depends on: domain-api, util-outbox
    ├── OutboxScheduler        (@Scheduled → calls OutboxProcessor per partition)
    └── OutboxStartupRecovery  (@Startup → resets stale PROCESSING tasks)

adapter-out-outbox
    ├── depends on: domain-api, util-outbox
    └── OutboxPortAdapter      (implements OutboxPort → delegates to OutboxRepository.enqueue)

adapter-out-spotify
    ├── depends on: util-outbox, domain-api
    └── Handlers for AppOutboxPartition.Spotify event types

domain-impl
    ├── depends on: domain-api, util-outbox
    └── Handlers for AppOutboxPartition.Domain event types
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
| Unit – handler logic   | `adapter-out-spotify`, `domain-impl` | Each handler in isolation with a mock Spotify/domain port |
| Integration – MongoDB  | `application-quarkus` | `MongoOutboxRepository` against embedded MongoDB; verifies claim atomicity, archive insertion |
| Contract – event types | `application-quarkus` | Asserts that each `AppOutboxEventType` has a registered handler; fails the build if a handler is missing |
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
