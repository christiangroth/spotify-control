# Starters – Architecture and Usage

## Overview

The `util-starters` module provides a mechanism for **one-time startup beans** that execute arbitrary logic exactly once during the application lifecycle. Typical use cases are data migrations, schema changes, or one-time bugfixes. Once a starter has succeeded, it is permanently skipped on subsequent restarts. A failed starter is retried on the next application start.

Starters are **never executed** in `dev` or `test` profiles. In those environments the completion flag is set immediately so the Quarkus scheduler is not blocked.

## Module Inventory

| Class / Interface             | Role                                                                                                          |
|-------------------------------|---------------------------------------------------------------------------------------------------------------|
| `Starter`                     | Interface for concrete starter implementations: `val id: String` + `fun execute()`.                          |
| `StarterStatus`               | `PENDING`, `SUCCEEDED`, `FAILED`.                                                                             |
| `StarterExecutionDocument`    | Embedded document recording a single execution attempt (startedAt, finishedAt, status, errorMessage).        |
| `StarterDocument`             | Panache entity mapped to the `starters` MongoDB collection; tracks per-starter state across all starts.       |
| `StarterDocumentRepository`   | Panache repository for `StarterDocument`.                                                                     |
| `StarterCompletionFlag`       | `@ApplicationScoped` bean backed by an `AtomicBoolean`; set to `true` when all starters have succeeded.      |
| `StarterSkipPredicate`        | Plain class with no-args constructor implementing `Scheduled.SkipPredicate`; referenced via `skipExecutionIf = StarterSkipPredicate::class`; returns `true` (skip) until the completion flag is set via CDI programmatic lookup. |
| `StarterService`              | Orchestrates starter execution: collects all `Starter` CDI beans, runs pending/failed starters in `id` order, persists results, records metrics. |
| `StarterStartup`              | `@Observes StartupEvent` bean that invokes `StarterService.runAll()` and sets the completion flag on full success. |

## Lifecycle

```
Application start (NORMAL / prod only)
    │
    ▼
StarterStartup.onStart()
    │
    ├── LaunchMode != NORMAL → mark completion flag immediately → return
    │
    ▼
StarterService.runAll()  (starters sorted by id)
    │
    ├── lastStatus = SUCCEEDED → skip
    ├── lastStatus = PENDING or FAILED (or no document) → execute
    │       ├── Success → persist StarterExecutionDocument (SUCCEEDED), update StarterDocument
    │       └── Failure → persist StarterExecutionDocument (FAILED, errorMessage), update StarterDocument
    │
    ▼
All starters SUCCEEDED?
    ├── yes → StarterCompletionFlag.markCompleted() → scheduler unblocked
    └── no  → flag remains false → scheduler blocked until next application start
```

## MongoDB Collection

| Collection | Purpose                                                                   |
|------------|---------------------------------------------------------------------------|
| `starters` | One document per starter; tracks `lastStatus` and full execution history. |

### `StarterDocument` fields

| Field        | Type                              | Description                                       |
|--------------|-----------------------------------|---------------------------------------------------|
| `starterId`  | `String` (BSON `_id`)            | Stable unique identifier defined by the starter.  |
| `lastStatus` | `String` (`StarterStatus` name)  | Most recent execution status.                     |
| `executions` | `List<StarterExecutionDocument>` | Full history of all execution attempts.           |

### `StarterExecutionDocument` fields

| Field          | Type       | Description                                           |
|----------------|------------|-------------------------------------------------------|
| `startedAt`    | `Instant`  | Timestamp when execution started.                     |
| `finishedAt`   | `Instant?` | Timestamp when execution finished (null if crashed).  |
| `status`       | `String`   | `SUCCEEDED` or `FAILED`.                              |
| `errorMessage` | `String?`  | Error detail if `status = FAILED`.                    |

## Scheduler Integration

All `@Scheduled` jobs in `adapter-in-scheduler` are annotated with `skipExecutionIf = StarterSkipPredicate::class`. The Quarkus scheduler calls `StarterSkipPredicate.test()` before each trigger; it returns `true` (skip) while `StarterCompletionFlag.isCompleted()` is `false`.

```kotlin
@Scheduled(cron = "0 0/10 * * * ?", skipExecutionIf = StarterSkipPredicate::class)
fun run() { ... }
```

## Metrics

| Metric name                          | Type    | Tags          | Description                                           |
|--------------------------------------|---------|---------------|-------------------------------------------------------|
| `starter_execution_duration_seconds` | `Timer` | `id`, `status`| Execution duration per starter and run.               |
| `starter_overall_status`             | `Gauge` | `id`          | `1` = SUCCEEDED, `0` = FAILED / PENDING (last known). |

## Profile Exclusion

`StarterStartup` checks `LaunchMode.current()` at runtime. If the mode is not `NORMAL` (i.e. `dev` or `test`), it sets the completion flag immediately without executing any starter, keeping the scheduler unblocked.

## Failure Handling

- A starter that throws any exception is marked `FAILED` and the error message is stored.
- On the next application start, a failed starter is retried.
- There is no automatic retry within a single application lifecycle.
- A permanently broken starter blocks the scheduler on every restart until it is fixed or its `StarterDocument` is manually set to `SUCCEEDED` in MongoDB.

## Architecture Placement

`util-starters` follows a similar self-contained utility module pattern as the external `de.chrgroth.quarkus.outbox` library:

| Module               | Responsibility                                                                                        |
|----------------------|-------------------------------------------------------------------------------------------------------|
| `util-starters`      | Core infrastructure: `Starter` interface, MongoDB documents, service, startup observer, metrics.      |
| `adapter-in-starter` | Concrete starter implementations acting as inbound adapters: receive a startup trigger from `util-starters` and call into the domain via port interfaces. |

## Defining a Starter

```kotlin
// adapter-in-starter
@ApplicationScoped
class AddMissingGenreFieldMigration(
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
) : Starter {

    override val id = "AddMissingGenreFieldMigration-v1"

    override fun execute() {
        recentlyPlayedRepository.backfillMissingGenreFields()
    }
}
```

**Invariants:**

1. Each `Starter` has a **stable, unique `id`** – changing the id creates a new starter record and re-runs the logic.
2. A starter with `lastStatus = SUCCEEDED` is **never executed again**.
3. A starter with `lastStatus = FAILED` or no existing document is **executed on the next start**.
4. Starters are **not executed** in `dev` or `test` profiles; the completion flag is set immediately so the scheduler is unblocked.
5. The Quarkus scheduler does **not fire any jobs** until all starters have completed successfully.
6. All execution attempts are **persisted** (with start time, end time, status, and optional error message).
7. Metrics are emitted for every execution duration and for the overall per-starter status.
