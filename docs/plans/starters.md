# Concept: One-Time Startup Beans (Starters)

## Purpose

Starters are one-time startup beans that execute arbitrary logic exactly once during the application lifecycle – typically for data migrations, schema changes, or one-time bugfixes. Once a starter has succeeded, it is permanently skipped on subsequent restarts. A failed starter is retried on the next application start.

Starters are **never executed** in the `dev` or `test` profiles, as those environments use ephemeral or controlled data sets.

---

## Lifecycle

```
Application start (prod only)
    │
    ▼
Load all known Starter beans
    │
    ▼
For each Starter (ordered by id):
    ├── Status = SUCCEEDED → skip
    ├── Status = PENDING or FAILED → execute
    │       ├── Record startedAt
    │       ├── Run logic
    │       │       ├── Success → record finishedAt, Status = SUCCEEDED
    │       │       └── Failure → record finishedAt, errorMessage, Status = FAILED
    │       └── Persist StarterExecution + update StarterDocument
    └── If any Starter remains PENDING or FAILED after the execution phase → block scheduler start
         │
         ▼
All Starters SUCCEEDED → resume scheduled jobs
```

---

## Entity Design

### `StarterDocument` (MongoDB collection: `starters`)

Tracks the overall state of a single starter across all application starts.

| Field          | Type                | Description                                      |
|----------------|---------------------|--------------------------------------------------|
| `starterId`    | `String` (BSON ID)  | Unique, stable identifier defined by the starter |
| `lastStatus`   | `StarterStatus`     | `PENDING`, `SUCCEEDED`, `FAILED`                 |
| `executions`   | `List<StarterExecutionDocument>` | Full history of all execution attempts  |

### `StarterExecutionDocument` (embedded)

One entry per execution attempt.

| Field          | Type       | Description                                        |
|----------------|------------|----------------------------------------------------|
| `startedAt`    | `Instant`  | Timestamp when execution started                   |
| `finishedAt`   | `Instant?` | Timestamp when execution finished (null if crashed)|
| `status`       | `StarterStatus` | `SUCCEEDED` or `FAILED`                       |
| `errorMessage` | `String?`  | Error detail if `status = FAILED`                  |

### `StarterStatus` (enum)

```
PENDING    – registered but never run
SUCCEEDED  – last execution completed without error
FAILED     – last execution threw an error
```

---

## Architecture Placement

Starters follow the same self-contained utility module pattern as `util-outbox`:

| Module              | Responsibility                                                                                         |
|---------------------|--------------------------------------------------------------------------------------------------------|
| `util-starters`     | `Starter` interface (`id: String`, `execute()`); `StarterStatus`; `StarterDocument`; `StarterExecutionDocument`; `StarterDocumentRepository`; `StarterService` (orchestration); `StarterStartup` (`@Observes StartupEvent`); `StarterCompletionFlag` (`@ApplicationScoped`, `AtomicBoolean`) |
| `adapter-in-starter`| Concrete starter implementations that trigger domain logic (e.g. data migration starters calling domain ports) |

`util-starters` is fully self-contained – it owns its own MongoDB documents and is not aware of any domain module. The `StarterService` collects all `Starter` instances at startup via CDI `Instance<Starter>`.

Concrete starters live in `adapter-in-starter` because they act as inbound adapters: they receive a startup trigger from `util-starters` and call into the domain via port interfaces, exactly as scheduled jobs in `adapter-in-scheduler` call domain ports.

---

## Scheduler Integration

The Quarkus scheduler must not fire any jobs until all starters have finished successfully. There are two options:

**Option A – `@SkipWhenAllStartersNotCompleted` (preferred)**

Implement a custom `io.quarkus.scheduler.SkipPredicate` that checks a shared `StarterCompletionFlag` (a simple `@ApplicationScoped` bean with an `AtomicBoolean`). Annotate each `@Scheduled` method with `@Scheduled(…, skipExecution = "allStartersCompleted")`. The flag is set to `true` by `StarterStartup` after all starters succeed.

**Option B – Startup ordering via CDI `@Priority`**

Give `StarterStartup` a high `@Priority` so it completes before the first scheduled trigger fires. This is simpler but relies on timing rather than an explicit gate.

Option A is preferred because it provides an explicit, testable contract.

---

## Profile Exclusion

Starters must **not** run in `dev` or `test` profiles. This is enforced by the `StarterStartup` bean:

```kotlin
@Inject
lateinit var profile: io.quarkus.runtime.LaunchMode  // or @ConfigProperty

@Observes
fun onStart(event: StartupEvent) {
    if (LaunchMode.current() != LaunchMode.NORMAL) {
        // set completion flag to true immediately so scheduler is unblocked
        return
    }
    // ... run starters
}
```

Alternatively, the `StarterStartup` bean can be excluded for non-prod profiles at build time using `@UnlessBuildProfile("dev")` and `@UnlessBuildProfile("test")` (from `io.quarkus.arc.profile`), but the runtime `LaunchMode` check is simpler and avoids maintaining multiple bean definitions.

---

## Metrics

The following Micrometer metrics are recorded under the `starter` group:

| Metric name                         | Type      | Tags                        | Description                              |
|-------------------------------------|-----------|-----------------------------|------------------------------------------|
| `starter_execution_duration_seconds`| `Timer`   | `id`, `status`              | Execution duration per starter and run   |
| `starter_overall_status`            | `Gauge`   | `id`                        | `1` = SUCCEEDED, `0` = FAILED / PENDING  |

The `Timer` is recorded by `StarterService` after each execution using `MeterRegistry`. The `Gauge` reflects the current `lastStatus` stored in `StarterDocument` and is refreshed after every execution.

---

## Failure Handling

- A starter that throws any exception has its `status` set to `FAILED` and the exception message stored in `errorMessage`.
- On the next application start the failed starter is retried (its entry exists but `lastStatus != SUCCEEDED`).
- There is no automatic retry within a single application lifecycle; retries happen at application restart.
- A permanently broken starter will block the scheduler on every restart until it is fixed or its `StarterDocument` is manually set to `SUCCEEDED` in MongoDB.

---

## Example: Defining a Starter

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

---

## Summary of Invariants

1. Each `Starter` has a **stable, unique `id`** – changing the id creates a new starter record and re-runs the logic.
2. A starter with `lastStatus = SUCCEEDED` is **never executed again**.
3. A starter with `lastStatus = FAILED` or no existing document is **executed on the next start**.
4. Starters are **not executed** in `dev` or `test` profiles; the completion flag is set immediately so the scheduler is unblocked in those environments.
5. The Quarkus scheduler does **not fire any jobs** until all starters have completed successfully.
6. All execution attempts are **persisted** (with start time, end time, status, and optional error message).
7. Metrics are emitted for every execution duration and for the overall per-starter status.
