# Outbox – Architecture and Usage

## Overview

The `util-outbox` module provides a persistent, reliable task queue backed by MongoDB. It decouples producers (domain services that enqueue tasks) from consumers (partition workers that dispatch them), ensuring at-least-once delivery even across application restarts.

## Core Concepts

| Concept              | Description                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Partition**        | An independent processing channel (e.g. `spotify`, `domain`). Each partition is processed serially. |
| **Event**            | Identifies the kind of work to be done and provides the deduplication key (e.g. `PollRecentlyPlayedPayload`). |
| **Payload**          | JSON-serialised event data passed to the handler.                                                |
| **Deduplication key** | A unique string per logical operation within a partition. Duplicate enqueues are silently skipped. |
| **Priority**         | `NORMAL` (default) or `HIGH`. HIGH-priority tasks are always claimed before NORMAL tasks.        |

## Module Inventory

| Class / Interface            | Role                                                                                 |
|------------------------------|--------------------------------------------------------------------------------------|
| `OutboxPartition`            | Interface for partition keys; extend with a sealed interface in `domain-api`.        |
| `OutboxEvent`                | Combined interface: `val key: String` (event type) + `fun deduplicationKey(): String`. Implemented by payload data classes. |
| `OutboxTask`                 | Immutable snapshot of a claimed task handed to the dispatch function.                |
| `OutboxTaskStatus`           | `PENDING`, `PROCESSING`, `DONE`, `FAILED`.                                           |
| `OutboxTaskPriority`         | `NORMAL`, `HIGH`.                                                                    |
| `OutboxPartitionStatus`      | `ACTIVE`, `PAUSED`.                                                                  |
| `OutboxError`                | Error value returned by the dispatch function on failure.                            |
| `RetryPolicy`                | Configures `maxAttempts` and the `backoff` delay list.                               |
| `OutboxRepository`           | Interface for all repository operations (claim, complete, fail, enqueue, …).         |
| `MongoOutboxRepository`      | MongoDB-backed `@ApplicationScoped` implementation of `OutboxRepository`.            |
| `OutboxPartitionInfo`        | Data class returned by `OutboxRepository.findPartition`.                             |
| `OutboxProcessor`            | Orchestrates claim → dispatch → complete/fail for one task at a time.                |
| `OutboxWakeupService`        | Holds a `Channel<Unit>(CONFLATED)` per partition; signals partition workers.         |
| `OutboxDocument`             | Panache entity mapped to the `outbox` MongoDB collection.                            |
| `OutboxArchiveDocument`      | Panache entity mapped to the `outbox_archive` MongoDB collection.                    |
| `OutboxPartitionDocument`    | Panache entity mapped to the `outbox_partitions` MongoDB collection.                 |

## Defining Partitions

Extend `OutboxPartition` with a sealed interface in `domain-api`:

```kotlin
sealed interface AppOutboxPartition : OutboxPartition {
    data object Spotify : AppOutboxPartition { override val key = "spotify" }
    data object Domain  : AppOutboxPartition { override val key = "domain"  }
}
```

## Defining Events and Payloads

Each payload class implements `OutboxEvent`, providing both the event type key and the deduplication key:

```kotlin
// domain-api
data class PollRecentlyPlayedPayload(
    val userId: String,
    val version: Int = 1,
) : OutboxEvent {
    override val key = "PollRecentlyPlayed"
    override fun deduplicationKey() = "PollRecentlyPlayed:$userId"
}
```

For events where only one pending instance should exist, return the key alone as the deduplication key:

```kotlin
data class RecomputeAggregationsPayload(val version: Int = 1) : OutboxEvent {
    override val key = "RecomputeAggregations"
    override fun deduplicationKey() = key
}
```

Sealing a parent interface over all payload types enables exhaustive `when` dispatch at compile time.

## Enqueueing Tasks

Inject `OutboxRepository` (or a port wrapping it) and call `enqueue`:

```kotlin
@ApplicationScoped
class OutboxPortAdapter(
    private val repository: OutboxRepository,
    private val wakeup: OutboxWakeupService,
    private val objectMapper: ObjectMapper,
) {
    fun enqueue(
        partition: AppOutboxPartition,
        payload: OutboxEvent,
        priority: OutboxTaskPriority = OutboxTaskPriority.NORMAL,
    ) {
        val json = objectMapper.writeValueAsString(payload)
        val inserted = repository.enqueue(partition, payload, json, priority)
        if (inserted) {
            wakeup.signal(partition)
        }
    }
}
```

Signal `OutboxWakeupService` after a successful insert so the partition worker wakes immediately.
No signal is sent for duplicate inserts (when `enqueue` returns `false`).

## Processing Tasks

Create one coroutine worker per partition, launched at application startup:

```kotlin
@ApplicationScoped
class OutboxPartitionWorker(
    private val wakeup: OutboxWakeupService,
    private val processor: OutboxProcessor,
    private val handlerPort: OutboxHandlerPort,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(partition: AppOutboxPartition) {
        scope.launch {
            val channel = wakeup.getOrCreate(partition)
            while (isActive) {
                channel.receive()
                do {
                    val processed = processor.processNext(partition, dispatch)
                } while (processed && isActive)
            }
        }
    }

    @PreDestroy
    fun stop() = scope.cancel()

    private val dispatch: (OutboxTask) -> Either<OutboxError, Unit> = { task ->
        when (task.eventType) {
            "PollRecentlyPlayed" -> handlerPort.handlePollRecentlyPlayed(deserialise(task.payload))
            // … exhaustive when over task.eventType string (or sealed AppOutboxEventType.fromKey)
            else -> Either.Left(OutboxError("Unknown event type: ${task.eventType}"))
        }
    }
}
```

## Startup Recovery

Reset stale `PROCESSING` tasks on startup, then signal all partition channels:

```kotlin
@ApplicationScoped
class OutboxStartupRecovery(
    private val repository: OutboxRepository,
    private val wakeup: OutboxWakeupService,
) {
    fun onStart(@Observes event: StartupEvent) {
        repository.resetStaleProcessingTasks()
        AppOutboxPartition.entries.forEach { wakeup.signal(it) }
    }
}
```

## Retry and Backoff

Configure `RetryPolicy` to control retry attempts and delays:

```kotlin
val policy = RetryPolicy(
    maxAttempts = 5,
    backoff = listOf(
        Duration.ofSeconds(5),
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofSeconds(60),
    ),
)
val processor = OutboxProcessor(repository, policy)
```

After `maxAttempts` failures the task is marked `FAILED` and stays in the `outbox` collection for manual replay.

## Partition Pause and Resume

Pause a partition when rate-limited:

```kotlin
repository.pausePartition(partition, "HTTP 429 – rate limited", Instant.now().plus(Duration.ofMinutes(1)))
```

Resume after the pause window expires:

```kotlin
scope.launch {
    delay(Duration.ofMinutes(1).toMillis())
    repository.activatePartition(partition)
    wakeup.signal(partition)
}
```

When paused, `OutboxRepository.claim` returns `null` for that partition. An absent `outbox_partitions` document is treated as `ACTIVE`, so no migration is required for existing deployments.

## MongoDB Collections

| Collection          | Purpose                                                       |
|---------------------|---------------------------------------------------------------|
| `outbox`            | Active tasks (PENDING, PROCESSING, FAILED).                   |
| `outbox_archive`    | Successfully completed tasks (audit log).                     |
| `outbox_partitions` | Partition pause/resume state (created lazily on first pause). |

## Priority

Enqueue time-sensitive tasks with `OutboxTaskPriority.HIGH`:

```kotlin
repository.enqueue(partition, payload, json, OutboxTaskPriority.HIGH)
```

HIGH-priority tasks are always claimed before NORMAL tasks within the same partition.
The claim query sorts by `priority` ascending. The enum names were chosen so that `HIGH` sorts
before `NORMAL` alphabetically (`H` < `N`), giving the correct semantic order without a numeric mapping.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Persistence backend | MongoDB (`findOneAndUpdate`) | Already present in the stack; atomic claim without an additional runtime dependency. |
| No Kafka / dedicated outbox library | Pure MongoDB | No additional infrastructure; at-most one application instance. |
| `kotlinx-coroutines-core` for wakeup | `Channel<Unit>(CONFLATED)` per partition | Zero idle CPU; zero extra latency; no polling fallback needed. |
| Single `OutboxEvent` interface | Combines event type key + deduplication key | Reduces the number of types callers must implement; payload class owns the dedup logic naturally. |
| Absent partition document = ACTIVE | Lazy creation on first pause | Existing deployments need no migration; only paused partitions need a document. |
| Priority via enum name ordering | `HIGH` < `NORMAL` alphabetically (ascending sort) | Avoids a numeric mapping; enum names are self-documenting. |
| Persistence co-located with core logic | Single `util-outbox` module | Simplicity over separation; can be split later if extraction is needed. |
