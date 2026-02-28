# Outbox Pattern – Usage Guide

## Overview

The `util-outbox` module provides a persistent, reliable task queue backed by MongoDB. It decouples producers (domain services that enqueue tasks) from consumers (partition workers that dispatch them), ensuring at-least-once delivery even across application restarts.

## Core Concepts

| Concept              | Description                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Partition**        | An independent processing channel (e.g. `spotify`, `domain`). Each partition is processed serially. |
| **Event type**       | Identifies the kind of work to be done (e.g. `PollRecentlyPlayed`).                             |
| **Payload**          | JSON-serialised task data; must include a `deduplicationKey` to prevent duplicate work.          |
| **Deduplication key** | A unique string per logical operation within a partition. Duplicate enqueues are silently skipped. |
| **Priority**         | `NORMAL` (default) or `HIGH`. HIGH-priority tasks are always claimed before NORMAL tasks.        |

## Module Inventory

| Class / Interface            | Role                                                                                 |
|------------------------------|--------------------------------------------------------------------------------------|
| `OutboxPartition`            | Interface for partition keys; extend with your own sealed interface.                 |
| `OutboxEventType`            | Interface for event type keys; extend with your own sealed interface.                |
| `OutboxPayload`              | Interface every payload class must implement; declares `deduplicationKey()`.         |
| `OutboxTask`                 | Immutable snapshot of a claimed task handed to the dispatch function.                |
| `OutboxTaskStatus`           | `PENDING`, `PROCESSING`, `DONE`, `FAILED`.                                           |
| `OutboxTaskPriority`         | `NORMAL`, `HIGH`.                                                                    |
| `OutboxPartitionStatus`      | `ACTIVE`, `PAUSED`.                                                                  |
| `OutboxError`                | Error value returned by the dispatch function on failure.                            |
| `RetryPolicy`                | Configures `maxAttempts` and the `backoff` delay list.                               |
| `OutboxRepository`           | Interface for all repository operations (claim, complete, fail, enqueue, …).         |
| `MongoOutboxRepository`      | MongoDB-backed `@ApplicationScoped` implementation of `OutboxRepository`.            |
| `OutboxPartitionInfo`        | Simple data class returned by `OutboxRepository.findPartition`.                      |
| `OutboxProcessor`            | Orchestrates claim → dispatch → complete/fail for one task at a time.                |
| `OutboxWakeupService`        | Holds a `Channel<Unit>(CONFLATED)` per partition; signals partition workers.         |
| `OutboxDocument`             | Panache entity mapped to the `outbox` MongoDB collection.                            |
| `OutboxArchiveDocument`      | Panache entity mapped to the `outbox_archive` MongoDB collection.                    |
| `OutboxPartitionDocument`    | Panache entity mapped to the `outbox_partitions` MongoDB collection.                 |

## Defining Partitions and Event Types

Extend the open interfaces in your own module (e.g. `domain-api`):

```kotlin
sealed interface AppOutboxPartition : OutboxPartition {
    data object Spotify : AppOutboxPartition { override val key = "spotify" }
    data object Domain  : AppOutboxPartition { override val key = "domain"  }
}

sealed interface AppOutboxEventType : OutboxEventType {
    data object PollRecentlyPlayed    : AppOutboxEventType { override val key = "PollRecentlyPlayed" }
    data object EnrichPlaybackEvents  : AppOutboxEventType { override val key = "EnrichPlaybackEvents" }
    // … add more as needed
}
```

Sealing these interfaces ensures an exhaustive `when` expression at compile time.

## Defining Payloads

Every payload class must implement `OutboxPayload`:

```kotlin
data class PollRecentlyPlayedPayload(
    val userId: String,
    val version: Int = 1,
) : OutboxPayload {
    override fun deduplicationKey() = "PollRecentlyPlayed:$userId"
}
```

The `deduplicationKey()` uniquely identifies the logical operation within its partition.
For tasks where only one pending instance should ever exist, return the event type name alone.

## Enqueueing Tasks

Inject `OutboxRepository` (or a thin port wrapping it) and call `enqueue`:

```kotlin
@ApplicationScoped
class OutboxPortAdapter(
    private val repository: OutboxRepository,
    private val wakeup: OutboxWakeupService,
    private val objectMapper: ObjectMapper,
) {
    fun enqueue(
        partition: AppOutboxPartition,
        eventType: AppOutboxEventType,
        payload: OutboxPayload,
        priority: OutboxTaskPriority = OutboxTaskPriority.NORMAL,
    ) {
        val json = objectMapper.writeValueAsString(payload)
        val inserted = repository.enqueue(partition, eventType, json, payload.deduplicationKey(), priority)
        if (inserted) {
            wakeup.signal(partition)
        }
    }
}
```

Signal `OutboxWakeupService` after a successful insert so the partition worker wakes immediately.

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
                    val processed = processor.processNext(partition, dispatch(partition))
                } while (processed && isActive)
            }
        }
    }

    @PreDestroy
    fun stop() = scope.cancel()

    private fun dispatch(partition: AppOutboxPartition): (OutboxTask) -> Either<OutboxError, Unit> = { task ->
        when (AppOutboxEventType.fromKey(task.eventType)) {
            AppOutboxEventType.PollRecentlyPlayed -> handlerPort.handlePollRecentlyPlayed(deserialise(task.payload))
            // … exhaustive when
        }
    }
}
```

The `Channel.CONFLATED` type coalesces multiple rapid signals into a single wakeup, so the worker drains the full queue before suspending again.

## Startup Recovery

On startup, reset any tasks stuck in `PROCESSING` (left over from a previous crash), then signal all partition channels:

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

Configure `RetryPolicy` to control how many times a failing task is retried:

```kotlin
val policy = RetryPolicy(
    maxAttempts = 5,
    backoff = listOf(5.seconds, 10.seconds, 30.seconds, 60.seconds).map { it.toJavaDuration() },
)
val processor = OutboxProcessor(repository, policy)
```

After `maxAttempts` failures the task is marked `FAILED` and remains in the `outbox` collection
for manual inspection or replay.

## Partition Pause and Resume

Pause a partition when rate-limited (e.g. HTTP 429 from an external API):

```kotlin
repository.pausePartition(partition, "HTTP 429 – rate limited", Instant.now().plus(Duration.ofMinutes(1)))
```

Resume with an inline coroutine:

```kotlin
scope.launch {
    delay(Duration.ofMinutes(1).toMillis())
    repository.activatePartition(partition)
    wakeup.signal(partition)
}
```

When paused, `OutboxRepository.claim` returns `null` for that partition, so the worker
suspends on `channel.receive()` without spinning.

## MongoDB Collections

| Collection          | Purpose                                                       |
|---------------------|---------------------------------------------------------------|
| `outbox`            | Active tasks (PENDING, PROCESSING, FAILED).                   |
| `outbox_archive`    | Successfully completed tasks (audit log).                     |
| `outbox_partitions` | Partition pause/resume state (created lazily on first pause). |

## Priority

Enqueue time-sensitive tasks with `OutboxTaskPriority.HIGH`:

```kotlin
repository.enqueue(partition, eventType, payload, deduplicationKey, OutboxTaskPriority.HIGH)
```

HIGH-priority tasks are always claimed before NORMAL tasks within the same partition.
The claim query sorts by `priority` ascending. The enum names were chosen so that `HIGH` sorts
before `NORMAL` alphabetically (`H` < `N`), giving the correct semantic order without requiring
a numeric mapping.
