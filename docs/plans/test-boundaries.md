# Test Concept: Testing at the Boundaries

## Context

The project currently mixes two distinct test styles without a clear rule for when to use which:

- **Plain unit tests with MockK** – live in `domain-impl`, mock all ports, run without any container.
- **`@QuarkusTest` integration tests** – live in `application-quarkus`, start a full Quarkus runtime with a real MongoDB container and a Spotify mock server.

Both styles are useful, but their placement is inconsistent and coverage has visible gaps.
This document describes a structured approach based on the *Test Your Boundaries* idea
and maps it explicitly to the hexagonal architecture of this project.

---

## The "Test Your Boundaries" Idea

> **Only test at the boundary of each component. Mock everything on the other side of that boundary.**

In a hexagonal architecture the boundaries are the **ports**. A test for a domain service
uses the inbound port as its entry point and mocks every outbound port. A test for a MongoDB
adapter uses the outbound port interface as the contract and talks to a real MongoDB instance –
it does not need the domain at all. Each layer is exercised in isolation, at its own boundary.

Benefits:
* Tests are small, focused, and fast where possible.
* The domain is always tested without any framework or infrastructure dependency.
* Adapter tests validate real integration behaviour (serialisation, query correctness, HTTP calls)
  without the complexity of a full end-to-end stack.
* The test suite scales: add new features by adding tests at the right boundary, not by
  expanding a single monolithic integration test.

---

## Test Layers

### Layer 1 – Domain logic (`domain-impl`)

**Goal:** verify that the domain services implement the business rules correctly.

| Property        | Value                                                       |
|-----------------|-------------------------------------------------------------|
| Entry point     | Inbound port interface (`*Port` in `domain-api`)            |
| Test doubles    | MockK mocks for every outbound port                         |
| Runtime         | Plain JVM, no container                                     |
| Module          | `domain-impl`                                               |
| Speed           | Fast (milliseconds per test)                                |

Every class in `domain-impl` must have a corresponding test class. The test instantiates the
adapter directly (no dependency injection), passes MockK mocks for all collaborators, and
verifies outcomes through the inbound port interface.

### Layer 2 – Outbound adapter tests (narrow integration)

**Goal:** verify that each outbound adapter correctly implements its port contract against
the real external system.

| Property        | Value                                                               |
|-----------------|---------------------------------------------------------------------|
| Entry point     | Outbound port interface (`*Port` in `domain-api`)                   |
| Test doubles    | None – the adapter under test talks to the real external dependency |
| Runtime         | `@QuarkusTest` with a real MongoDB / real (or mock) Spotify server  |
| Module          | `application-quarkus` (adapter tests require Quarkus DI + infra)   |
| Speed           | Medium (seconds per test; infrastructure startup amortised)         |

Tests inject the port interface (not the implementation) via `@Inject`. They validate
persistence round-trips, query correctness, serialisation, and error handling – without
invoking any domain logic.

**Current coverage:**

| Adapter                         | Port                              | Test class                        | Status  |
|---------------------------------|-----------------------------------|-----------------------------------|---------|
| `MongoUserRepository`           | `UserRepositoryPort`              | `UserRepositoryTests`             | ✅ done |
| `RecentlyPlayedRepositoryAdapter` | `RecentlyPlayedRepositoryPort`  | `RecentlyPlayedRepositoryTests`   | ✅ done |
| `SpotifyAuthAdapter`            | `SpotifyAuthPort`                 | –                                 | ❌ missing |
| `SpotifyRecentlyPlayedAdapter`  | `SpotifyRecentlyPlayedPort`       | –                                 | ❌ missing |

### Layer 3 – Inbound adapter tests (narrow integration)

**Goal:** verify that each inbound adapter correctly translates external triggers (HTTP
requests, scheduled ticks) into domain port calls and maps responses back.

| Property        | Value                                                                |
|-----------------|----------------------------------------------------------------------|
| Entry point     | HTTP endpoint or scheduler trigger                                   |
| Test doubles    | Domain ports mocked via Quarkus CDI overrides where possible;
                    `@QuarkusTest` with `@TestSecurity` for auth scenarios               |
| Runtime         | `@QuarkusTest` + REST Assured / `@InjectMock`                        |
| Module          | `application-quarkus`                                                |
| Speed           | Medium                                                               |

**Current coverage:**

| Adapter                         | Test class(es)                                        | Status         |
|---------------------------------|-------------------------------------------------------|----------------|
| `LoginResource`                 | `LoginPageTests`                                      | ✅ done        |
| `OAuthResource`                 | `OAuthFlowTests`                                      | ✅ done        |
| `DashboardResource`             | `DashboardPageTests`                                  | ✅ done        |
| `DocsResource`                  | `DocsPageTests`                                       | ✅ done        |
| `SampleUsecaseApi`              | `SampleUsecaseApiTests`                               | ✅ done        |
| `SpotifyCookieAuthMechanism`    | covered implicitly by `OAuthFlowTests`                | ⚠️ implicit    |
| `RecentlyPlayedFetchJob`        | –                                                     | ❌ missing     |
| `UserProfileUpdateJob`          | –                                                     | ❌ missing     |

### Layer 4 – Application wiring (smoke tests)

**Goal:** confirm that all beans are wired correctly and the application starts cleanly.

| Property        | Value                                     |
|-----------------|-------------------------------------------|
| Entry point     | HTTP health / metrics endpoints           |
| Test doubles    | None                                      |
| Runtime         | `@QuarkusTest`                            |
| Module          | `application-quarkus`                     |
| Speed           | Fast (single request against running app) |

**Current coverage:** `HealthCheckTests`, `MetricsTests` – ✅ done.

---

## Coverage Map

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  adapter-in-web / adapter-in-scheduler   (Layer 3)                             │
│                                                                                 │
│  LoginResource ✅  OAuthResource ✅  DashboardResource ✅  DocsResource ✅      │
│  SampleUsecaseApi ✅  SpotifyCookieAuthMechanism ⚠️                             │
│  RecentlyPlayedFetchJob ❌  UserProfileUpdateJob ❌                             │
└───────────────────────────┬─────────────────────────────────────────────────────┘
                            │ calls via inbound ports (domain-api)
┌───────────────────────────▼─────────────────────────────────────────────────────┐
│  domain-impl   (Layer 1)                                                        │
│                                                                                 │
│  LoginServiceAdapter ✅  SpotifyAccessTokenService ✅  TokenEncryptionAdapter ✅ │
│  RecentlyPlayedAdapter ✅  UserProfileUpdateAdapter ✅                           │
└───────────────────────────┬─────────────────────────────────────────────────────┘
                            │ calls via outbound ports (domain-api)
┌───────────────────────────▼─────────────────────────────────────────────────────┐
│  adapter-out-mongodb / adapter-out-spotify   (Layer 2)                         │
│                                                                                 │
│  MongoUserRepository ✅  RecentlyPlayedRepositoryAdapter ✅                     │
│  SpotifyAuthAdapter ❌  SpotifyRecentlyPlayedAdapter ❌                          │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Identified Gaps

### G1 – `SpotifyAuthAdapter` and `SpotifyRecentlyPlayedAdapter` (Layer 2 – outbound)

Neither Spotify adapter has any tests. These adapters perform HTTP calls to the Spotify API
(or the existing `SpotifyMockResource` in dev/test mode). Missing coverage means that
serialisation bugs, incorrect request construction, and error-response handling are invisible
to the test suite.

**Proposed approach:** add `@QuarkusTest` tests in `application-quarkus` that inject
`SpotifyAuthPort` and `SpotifyRecentlyPlayedPort` and exercise them against the existing
Spotify mock server that is already registered as a dev service (`SpotifyMockResource`).

### G2 – `RecentlyPlayedFetchJob` and `UserProfileUpdateJob` (Layer 3 – inbound scheduler)

Neither scheduler job has tests. The jobs are the entry point for background polling, so any
logic bug (wrong port call, error not caught) would silently affect production behaviour.

**Proposed approach:** add `@QuarkusTest` tests that use Quarkus CDI mocks (`@InjectMock`)
to verify that each job calls the expected domain port method exactly once per invocation
and that exceptions are handled gracefully (not propagated as uncaught exceptions).

### G3 – `UserServiceTests` uses `@QuarkusTest` unnecessarily (Layer 1 classification)

`UserServiceTests` in `application-quarkus` tests `UserServicePort.isAllowed()`, which is a
pure domain rule backed by configuration (allow-list). There are no infrastructure calls.
Using `@QuarkusTest` makes this test slower and couples it to the Quarkus runtime without any
benefit.

**Proposed approach:** move this test to `domain-impl` as a plain unit test, instantiating
the service directly and supplying the allow-list via the constructor.

### G4 – Adapter tests live in `application-quarkus` instead of their own modules

Tests for `adapter-out-mongodb` and `adapter-in-web` (and the future Spotify adapter tests)
all reside in `application-quarkus`. This is technically necessary today because `@QuarkusTest`
requires the full application module. The trade-off is accepted for now.

**Note:** if a dedicated Quarkus test infrastructure module is introduced in the future, adapter
tests can be moved closer to their production code without affecting coverage.

---

## Suggested Actions

The following actions bring the test suite into full alignment with the concept above,
ordered from highest to lowest value.

### S1 – Remove `UserServiceTests` (duplicate / wrong layer)

`UserServiceTests` in `application-quarkus` tests a pure configuration-backed domain rule.
It is slow (requires a full Quarkus runtime) and offers no benefit over a plain unit test.
Replace it with a new `UserServiceAdapterTests` in `domain-impl` that constructs the service
directly with a hard-coded allow-list, then **delete** `UserServiceTests` entirely.

*Impact: one test class replaced and removed, faster CI.*

### S2 – Add `SpotifyAuthAdapterTests` (Layer 2 – missing)

Add a `@QuarkusTest` test class in `application-quarkus` that injects `SpotifyAuthPort`
and exercises it against the existing Spotify mock server (`SpotifyMockResource`).
Key cases: `exchangeCode` happy path, `getUserProfile` happy path, token refresh, error responses.

*Impact: covers the auth adapter boundary, currently completely untested.*

### S3 – Add `SpotifyRecentlyPlayedAdapterTests` (Layer 2 – missing)

Same approach as S2. Inject `SpotifyRecentlyPlayedPort` and test it against the Spotify mock.
Key cases: items returned, empty list, error response (HTTP 4xx/5xx from mock).

*Impact: covers the recently-played adapter boundary, currently completely untested.*

### S4 – Add `RecentlyPlayedFetchJobTests` (Layer 3 – missing)

Add a `@QuarkusTest` test class that uses `@InjectMock` to mock `RecentlyPlayedPort`
(the inbound domain port that the job depends on).
Verify that the job calls `fetchAndPersistForAllUsers()` exactly once per invocation and that
any exception is swallowed (not propagated to the Quarkus scheduler).

*Impact: covers the scheduler entry point for playback tracking.*

### S5 – Add `UserProfileUpdateJobTests` (Layer 3 – missing)

Same approach as S4 for `UserProfileUpdateJob`, mocking `UserProfileUpdatePort`.

*Impact: covers the scheduler entry point for profile sync.*

---

## Duplicate Tests

The table below lists tests that overlap with another test at the same or a better layer
and are candidates for removal or consolidation.

| Test class         | Module                | Issue and recommended action                                                            |
|--------------------|-----------------------|-----------------------------------------------------------------------------------------|
| `UserServiceTests` | `application-quarkus` | Tests a pure domain rule with `@QuarkusTest` — no infrastructure is exercised. The allow-list logic is also verified indirectly by `OAuthFlowTests`. Replace with a plain unit test in `domain-impl` (see S1), then delete this class. |

No other test classes are genuine duplicates. `LoginServiceAdapterTests` (Layer 1) and
`OAuthFlowTests` (Layer 3) appear to cover similar ground but they test different boundaries:
the former checks domain logic in isolation, the latter checks that the HTTP adapter correctly
drives the domain. Both are necessary.

---

## Summary Table

| Layer                   | Module(s)                | Mechanism                     | Speed   | Status          |
|-------------------------|--------------------------|-------------------------------|---------|-----------------|
| 1 – Domain logic        | `domain-impl`            | Unit test + MockK             | Fast    | ✅ complete     |
| 2 – Outbound adapters   | `application-quarkus`    | `@QuarkusTest` + real infra   | Medium  | ⚠️ partial (S2, S3) |
| 3 – Inbound adapters    | `application-quarkus`    | `@QuarkusTest` + REST Assured | Medium  | ⚠️ partial (S4, S5) |
| 4 – Application wiring  | `application-quarkus`    | `@QuarkusTest` smoke          | Fast    | ✅ complete     |

**Immediate wins (no new code, only restructuring):** S1 removes a slow `@QuarkusTest` for
pure domain logic and closes the Layer 1 gap for `UserService`. All other actions require
new test classes.
