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
    data object RecentlyPlayed : AppOutboxPartition { override val key = "recently-played" }
    data object UserProfileUpdate : AppOutboxPartition { override val key = "user-profile-update" }

    companion object {
        val all: List<AppOutboxPartition> = listOf(RecentlyPlayed, UserProfileUpdate)
    }
}
```

## Defining Events and Payloads

Implement `OutboxEvent` within a sealed interface in `domain-api`. Each event type carries its own
partition and priority, enabling exhaustive `when` dispatch at compile time:

```kotlin
sealed interface AppOutboxEvent : OutboxEvent {
    val partition: AppOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL

    data object FetchRecentlyPlayed : AppOutboxEvent {
        override val key = "FetchRecentlyPlayed"
        override fun deduplicationKey() = "FetchRecentlyPlayed"
        override val partition = AppOutboxPartition.RecentlyPlayed
        override val priority = OutboxTaskPriority.HIGH
    }

    data object UpdateUserProfiles : AppOutboxEvent {
        override val key = "UpdateUserProfiles"
        override fun deduplicationKey() = "UpdateUserProfiles"
        override val partition = AppOutboxPartition.UserProfileUpdate
    }

    companion object {
        fun fromKey(key: String): AppOutboxEvent = when (key) {
            FetchRecentlyPlayed.key -> FetchRecentlyPlayed
            UpdateUserProfiles.key -> UpdateUserProfiles
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
```

For events where only one pending instance should exist, return the key alone as the deduplication key.
For per-entity events, include the entity identifier (e.g. `"SyncPlaylist:$playlistId"`).

## Enqueueing Tasks

Enqueue via `OutboxPort` (in `domain-api`), implemented by `OutboxPortAdapter` in `adapter-out-outbox`:

```kotlin
// caller (e.g. adapter-in-scheduler)
outboxPort.enqueue(AppOutboxEvent.FetchRecentlyPlayed)
```

`OutboxPortAdapter` delegates to `OutboxRepository.enqueue` and signals the wakeup channel:

```kotlin
val inserted = repository.enqueue(event.partition, event, "{}", event.priority)
if (inserted) wakeupService.signal(event.partition)
```

## Processing Tasks

`OutboxPartitionWorker` in `adapter-in-outbox` launches one coroutine per partition at startup.
Each coroutine waits for a wakeup signal, then drains the partition queue:

```kotlin
val channel = wakeupService.getOrCreate(partition)
while (isActive) {
    channel.receive()
    var processed: Boolean
    do {
        processed = outboxProcessor.processNext(partition) { task -> dispatch(task) }
    } while (processed && isActive)
}
```

The `dispatch` function uses an exhaustive `when` on `AppOutboxEvent.fromKey(task.eventType)`:

```kotlin
when (AppOutboxEvent.fromKey(task.eventType)) {
    is AppOutboxEvent.FetchRecentlyPlayed -> handlerPort.handleFetchRecentlyPlayed()
    is AppOutboxEvent.UpdateUserProfiles  -> handlerPort.handleUpdateUserProfiles()
    // Sealed when – compiler error if a new AppOutboxEvent is added without a branch
}
```

`OutboxHandlerPort` (in `domain-api`) is implemented by `OutboxHandlerAdapter` in `domain-impl`.

## Startup Recovery

`OutboxStartupRecovery` in `adapter-in-outbox` runs at application startup: it calls
`OutboxRepository.resetStaleProcessingTasks()` to recover tasks left in `PROCESSING` status
from a previous crash, then signals every partition channel so workers process any recovered tasks.

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
