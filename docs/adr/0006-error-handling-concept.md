# Error Handling: DomainResult and DomainError

* Status: accepted
* Deciders: Chris
* Date: 2026-02-26

Technical Story: https://github.com/christiangroth/spotify-control/issues/47

## Context and Problem Statement

The application mixes two incompatible error-signalling approaches: ad-hoc sealed result classes
(e.g. `LoginResult`) for some ports, and uncaught exceptions (`check()`, `require()`,
`IllegalStateException`) for others. Failures carry plain `String` messages with no enforced
naming convention, so the frontend cannot map errors to localised messages reliably, and technical
exceptions from infrastructure adapters leak across domain boundaries uncontrolled.

How should the application uniformly represent and propagate domain failures across all ports and
adapters?

## Decision Drivers

* All failures must be type-safe and enumerable at compile time.
* Error codes must be stable and unique so the frontend can map them to localised messages.
* Technical exceptions from infrastructure adapters must not cross port boundaries.
* The pattern must be simple and consistent with the existing hexagonal architecture.
* Minimal external dependencies for a solo-developer project.

## Considered Options

1. **Custom `DomainResult<T>` + `DomainError` enums (no external dependency)**
2. **Arrow `Either<DomainError, T>` with Arrow Core library**
3. **Kotlin stdlib `Result<T>` with typed exceptions**

## Decision Outcome

Chosen option: **"Custom `DomainResult<T>` + `DomainError` enums"**, because it satisfies all
decision drivers without adding an external dependency, is consistent with the existing Kotlin
idioms in the codebase, and is simple enough for an AI-assisted solo-developer workflow.

### Positive Consequences

* Single, consistent result type across all ports: `DomainResult<T>`.
* Errors are compile-time typed; the exhaustive `when` in Kotlin enforces handling of both
  `Success` and `Failure`.
* Each `DomainError` code (e.g. `AUTH-001`) is stable and unique – the frontend can map codes to
  messages without coupling to backend internals.
* No new runtime dependency; the implementation is a handful of lines in `domain-api`.
* Exceptions from infrastructure adapters are caught at the adapter boundary and converted to
  typed `DomainResult.Failure` values before crossing the port.

### Negative Consequences

* `DomainResult` lacks the rich combinator API (`flatMap`, `recover`, `zip`) that Arrow provides
  out of the box; helpers must be added incrementally as needed.
* Developers accustomed to Arrow's `raise` DSL will need to use `when` pattern matching instead.

## Pros and Cons of the Options

### Custom `DomainResult<T>` + `DomainError` enums

* Good, because no external dependency is added.
* Good, because consistent with existing Kotlin sealed class patterns in the codebase.
* Good, because simple to understand and maintain for a solo developer and AI coding agents.
* Good, because `DomainError` enum members enforce unique error codes via the prefix convention.
* Bad, because combinators (`flatMap`, `zip`, `recover`) must be added manually if needed.

### Arrow `Either<DomainError, T>`

* Good, because `Either` is a widely understood functional abstraction.
* Good, because Arrow provides a rich combinator API and coroutine integration out of the box.
* Good, because the `either { }` / `raise` DSL enables readable sequential error handling.
* Bad, because adds Arrow as an external dependency (multiple artifacts, ~1 MB added to classpath).
* Bad, because Arrow's functional patterns have a steeper learning curve and produce less
  predictable output from AI coding agents.
* Bad, because mixing `Either` with Jakarta EE CDI and JAX-RS responses requires additional
  adapter glue.

### Kotlin stdlib `Result<T>` with typed exceptions

* Good, because no external dependency; `Result<T>` is part of the Kotlin stdlib.
* Bad, because `Result.Failure` carries a `Throwable`, not a typed `DomainError`; error codes
  require an exception hierarchy rather than plain enums.
* Bad, because exception hierarchies for domain errors conflict with the principle that
  domain failures are expected outcomes, not exceptional conditions.
* Bad, because `Result<T>` is designed for coroutine and async use cases; its API (`.getOrThrow()`,
  `.exceptionOrNull()`) encourages exception-oriented thinking.

## Links

* [Error handling concept](../plans/errorhandling.md)
* [Hexagonal architecture ADR](0002-backend-hexagonal-architecture.md)
* [Arrow documentation](https://arrow-kt.io)
