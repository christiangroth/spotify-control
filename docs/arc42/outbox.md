# Outbox – Architecture and Usage

## Overview

The `util-outbox` module provides a persistent, reliable task queue backed by MongoDB. It decouples producers (domain services that enqueue tasks) from consumers (partition workers that dispatch them), ensuring at-least-once delivery even across application restarts.

## Core Concepts

| Concept              | Description                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Partition**        | An independent processing channel. Each partition is processed serially. |
| **Event**            | Identifies the kind of work to be done and provides the deduplication key. |
| **Payload**          | String data passed to the handler (e.g. `userId`).                                               |
| **Deduplication key** | A unique string per logical operation within a partition. Duplicate enqueues are silently skipped. |
| **Priority**         | `NORMAL` (default) or `HIGH`. HIGH-priority tasks are always claimed before NORMAL tasks.        |
| **Throttle interval** | Optional minimum delay between consecutive task dispatches within a partition (see [Throttling](#throttling)). |

## Module Inventory

| Class / Interface            | Role                                                                                 |
|------------------------------|--------------------------------------------------------------------------------------|
| `OutboxPartition`            | Interface for partition keys; extend with a sealed interface in `domain-api`.        |
| `OutboxEvent`                | Combined interface: `val key: String` + `fun deduplicationKey(): String`.            |
| `OutboxTask`                 | Immutable snapshot of a claimed task handed to the dispatch function.                |
| `OutboxTaskStatus`           | `PENDING`, `PROCESSING`, `DONE`, `FAILED`.                                           |
| `OutboxTaskPriority`         | `NORMAL`, `HIGH`.                                                                    |
| `OutboxPartitionStatus`      | `ACTIVE`, `PAUSED`.                                                                  |
| `OutboxError`                | Error value returned by the dispatch function on failure.                            |
| `RetryPolicy`                | Configures `maxAttempts` and the `backoff` delay list.                               |
| `OutboxRepository`           | Interface for all repository operations (claim, complete, fail, enqueue, …).         |
| `MongoOutboxRepository`      | MongoDB-backed `@ApplicationScoped` implementation of `OutboxRepository`.            |
| `Outbox`                     | Facade combining `OutboxRepository` and `OutboxWakeupService`; primary entry point for adapters. |
| `OutboxProcessor`            | Orchestrates claim → dispatch → complete/fail for one task at a time.                |
| `OutboxWakeupService`        | Holds a `Channel<Unit>(CONFLATED)` per partition; signals partition workers.         |
| `OutboxDocument`             | Panache entity mapped to the `outbox` MongoDB collection.                            |
| `OutboxArchiveDocument`      | Panache entity mapped to the `outbox_archive` MongoDB collection.                    |
| `OutboxPartitionDocument`    | Panache entity mapped to the `outbox_partitions` MongoDB collection.                 |
| `OutboxArchiveCleanupJob`    | Scheduled job that deletes old archive entries nightly (configurable retention).     |

## Defining Partitions and Events

Extend `OutboxPartition` and `OutboxEvent` in `domain-api`. Each event carries its own partition,
priority, deduplication key, and payload. Example skeleton:

```kotlin
sealed interface DomainOutboxPartition : OutboxPartition { ... }

sealed interface DomainOutboxEvent : OutboxEvent {
    val partition: DomainOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL
    fun toPayload(): String

    data class FetchRecentlyPlayed(val userId: String) : DomainOutboxEvent {
        override val key = "FetchRecentlyPlayed"
        override fun deduplicationKey() = "$key:$userId"
        override val partition = DomainOutboxPartition.ToSpotify
        override val priority = OutboxTaskPriority.HIGH
        override fun toPayload() = userId
    }
    // ...
    companion object {
        fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) { ... }
    }
}
```

## Enqueueing Tasks

Enqueue via `OutboxPort` (in `domain-api`), implemented by `OutboxPortAdapter` in `adapter-out-outbox`,
which delegates to the `Outbox` facade in `util-outbox`:

```kotlin
outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(userId))
```

## Processing Tasks

`OutboxPartitionWorker` in `adapter-in-outbox` launches one coroutine per partition. Each coroutine
waits on `outbox.getOrCreateChannel(partition).receive()`, then drains the queue via
`outboxProcessor.processNext(partition) { task -> dispatch(task) }`.

The `dispatch` function uses an exhaustive `when` on `DomainOutboxEvent.fromKey(...)`:

```kotlin
when (event) {
    is DomainOutboxEvent.FetchRecentlyPlayed -> handlerPort.handle(event)
    is DomainOutboxEvent.UpdateUserProfile   -> handlerPort.handle(event)
}
```

`OutboxHandlerPort` (in `domain-api`) is implemented by `OutboxHandlerAdapter` in `domain-impl`.

## Startup Recovery

`OutboxStartupRecovery` in `adapter-in-outbox` runs at startup with `@Priority(1)` (before the
partition workers start). It resets stale `PROCESSING` tasks, then for each partition:
- **Paused, `pausedUntil > now`**: launches a delayed coroutine that activates the partition when
  the pause expires and signals the channel.
- **Paused, `pausedUntil <= now`**: activates immediately and signals.
- **Active**: signals directly.

## Retry and Backoff

Configure `RetryPolicy` with `maxAttempts` and a `backoff` delay list. After `maxAttempts` failures
the task is moved to the archive with status `FAILED`.

## Partition Pause and Resume

Call `outbox.activatePartition(partition)` / use `OutboxRepository.pausePartition(...)` to pause.
When paused, `OutboxRepository.claim` returns `null` for that partition.

### Exception: Non-Pausing Partitions

Partitions may override `pauseOnRateLimit = false` (defined in the `OutboxPartition` interface) to opt
out of the pause-on-rate-limit behaviour. When a rate-limited response is received for such a partition:

- The partition is **not** paused; other tasks continue to be processed immediately.
- The affected task is rescheduled using the rate-limit delay (via `OutboxRepository.reschedule`).

This is used by the `to-spotify-recently-played` partition to ensure that playback history is never
missed due to a temporary rate-limit response. Because the Spotify recently-played window is limited
to the last ~50 tracks, pausing the partition even briefly risks losing playback events that would
age out of the Spotify API window before the partition resumes.

## Throttling

Partitions may declare a `throttleInterval: Duration` to proactively limit the rate at which tasks
are dispatched.  When set, the partition worker inserts a fixed delay **after each successfully
claimed task** before attempting to claim the next one.  This prevents Spotify rate-limiting by
keeping the outgoing request rate below the API quota.

The `to-spotify` partition is configured with a throttle interval of **1 second per request**:

```kotlin
data object ToSpotify : DomainOutboxPartition {
    override val key = "to-spotify"
    override val throttleInterval: Duration = Duration.ofSeconds(1)
}
```

The `to-spotify-playback` partition has no throttle interval (defaults to `null`) because
playback-history fetches are time-sensitive and must not be delayed beyond the Spotify
recently-played window.

## Archive Cleanup

`OutboxArchiveCleanupJob` runs every night at 01:00 and removes archive entries whose `completedAt`
timestamp is older than the configured retention period:

```properties
app.outbox.archive-retention-days=365
```

The default value is 365 days.

## MongoDB Collections

| Collection          | Purpose                                                       |
|---------------------|---------------------------------------------------------------|
| `outbox`            | Active tasks (PENDING, PROCESSING).                           |
| `outbox_archive`    | Completed tasks: successfully processed (DONE) and exhausted failed tasks (FAILED). |
| `outbox_partitions` | Partition pause/resume state (created lazily on first pause). |

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Persistence backend | MongoDB (`findOneAndUpdate`) | Already present in the stack; atomic claim without an additional runtime dependency. |
| No Kafka / dedicated outbox library | Pure MongoDB | No additional infrastructure; at-most one application instance. |
| `kotlinx-coroutines-core` for wakeup | `Channel<Unit>(CONFLATED)` per partition | Zero idle CPU; zero extra latency; no polling fallback needed. |
| Single `OutboxEvent` interface | Combines event type key + deduplication key | Reduces the number of types callers must implement; payload class owns the dedup logic naturally. |
| Absent partition document = ACTIVE | Lazy creation on first pause | Existing deployments need no migration; only paused partitions need a document. |
| Priority via enum name ordering | `HIGH` < `NORMAL` alphabetically (ascending sort) | Avoids a numeric mapping; enum names are self-documenting. |
| `Outbox` facade in `util-outbox` | Encapsulates `OutboxRepository` + `OutboxWakeupService` | Adapters depend on a single bean; wakeup signalling is co-located with enqueue. |
| Throttle interval in `OutboxPartition` | Per-partition `Duration?` property (default `null`) | Each partition can declare its own rate budget; the worker applies the delay without coupling throttling logic to the processor or repository. |
