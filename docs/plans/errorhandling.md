# Concept: Error Handling

## Current State

The application currently uses two distinct error handling mechanisms:

1. **Sealed result classes** (ad-hoc, per use-case): `LoginResult` in `LoginServicePort` models
   success and failure as separate subtypes. The `Failure` variant carries a plain `String` error
   message with no enforced naming convention.

2. **Kotlin invariant checks** (`check()` / `require()`): Used in adapters (e.g.
   `SpotifyAuthAdapter`) to assert HTTP responses are as expected. These throw
   `IllegalStateException` / `IllegalArgumentException` on failure and propagate as unhandled
   exceptions across layer boundaries.

### Problems

- The sealed class pattern is not applied consistently – most ports return raw domain objects and
  throw exceptions on failure.
- `Failure(error: String)` provides no type safety; callers cannot enumerate possible errors at
  compile time, and the frontend cannot map error codes to localised messages reliably.
- Technical exceptions from infrastructure adapters bubble up through the domain into the web
  adapter uncontrolled.
- There is no shared vocabulary for errors across modules.

---

## Goals

- A single, generic result type that replaces all ad-hoc sealed classes.
- All domain errors are typed: the caller knows at compile time what failures are possible.
- Error codes follow a stable naming convention so the frontend can map them to localised messages.
- Technical / infrastructure exceptions are caught at the adapter boundary and converted to typed
  domain errors before crossing the port.
- Minimal external dependencies; the pattern must be simple enough for a solo-developer project.

---

## Proposed Design

### `DomainError` – sealed interface implemented by enums

Every failure is a `DomainError`. Each domain area defines its own `enum class` that implements
`DomainError`. The `code` property exposes a unique, stable string used as the error identifier.

**Prefix convention:** `<AREA>-<NNN>` where `<AREA>` is a short uppercase acronym for the domain
area and `<NNN>` is a zero-padded three-digit number unique within that area.

```kotlin
sealed interface DomainError {
    val code: String
}
```

Example domain-area enums (live in `domain-api`):

```kotlin
enum class AuthError(override val code: String) : DomainError {
    USER_NOT_ALLOWED("AUTH-001"),
    TOKEN_EXCHANGE_FAILED("AUTH-002"),
    PROFILE_FETCH_FAILED("AUTH-003"),
    ;
}

enum class UserError(override val code: String) : DomainError {
    NOT_FOUND("USER-001"),
    ;
}

enum class TokenError(override val code: String) : DomainError {
    ENCRYPTION_FAILED("TOKEN-001"),
    DECRYPTION_FAILED("TOKEN-002"),
    INVALID_FORMAT("TOKEN-003"),
    ;
}
```

Rules:
- **Prefix uniqueness** – every `<AREA>` prefix is unique across the whole codebase (enforced by
  code review / convention, validated by a unit test that loads all `DomainError` subtypes via
  reflection and asserts no code collision).
- **Code stability** – once published, a code must never be renumbered. Deprecated codes are kept
  as `DEPRECATED_<NAME>` so old frontend mappings remain valid.
- **No free-text messages** – `DomainError` carries only the code. Human-readable messages live
  in the frontend.

### `DomainResult<T>` – generic result type

```kotlin
sealed class DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>()
    data class Failure(val error: DomainError) : DomainResult<Nothing>()

    fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): DomainError? = (this as? Failure)?.error
}
```

- Replaces all ad-hoc sealed result classes (starting with `LoginResult`).
- Port interfaces return `DomainResult<T>` instead of raw objects or ad-hoc sealed classes.
- `LoginResult` becomes a type alias for readability where helpful:
  `typealias LoginResult = DomainResult<UserId>`

### Adapter boundary rule

Adapters that call external systems (`adapter-out-spotify`, `adapter-out-mongodb`,
`adapter-out-*`) must catch all exceptions internally and return an appropriate `DomainResult.Failure`
instead of letting exceptions cross the port boundary.

```kotlin
// Before
override fun exchangeCode(code: String): SpotifyTokens {
    val response = httpClient.send(...)
    check(response.statusCode() == HTTP_OK) { "Spotify token exchange failed: ..." }
    ...
}

// After
override fun exchangeCode(code: String): DomainResult<SpotifyTokens> = try {
    val response = httpClient.send(...)
    if (response.statusCode() != HTTP_OK)
        return DomainResult.Failure(AuthError.TOKEN_EXCHANGE_FAILED)
    DomainResult.Success(parseTokens(response.body()))
} catch (e: Exception) {
    logger.error(e) { "Unexpected error during token exchange" }
    DomainResult.Failure(AuthError.TOKEN_EXCHANGE_FAILED)
}
```

Domain services propagate `DomainResult` using `map` / `flatMap` (or pattern matching) and return
their own `DomainResult` to the web adapter, which translates failures to HTTP error responses or
redirects.

---

## Arrow Library Evaluation

[Arrow](https://arrow-kt.io) is a functional programming library for Kotlin. Its `Either<E, A>`
type is semantically equivalent to the proposed `DomainResult<T>` (`Left` = failure, `Right` =
success) and provides a richer API: `map`, `flatMap`, `fold`, `zip`, `recover`, `raise`-based DSL,
and integration with Kotlin coroutines.

### Reasons to adopt Arrow

- Rich combinator API reduces boilerplate when chaining multiple fallible operations.
- `raise` / `either { }` DSL enables sequential, exception-style code that is still type-safe.
- Actively maintained with a large community.
- `Either` is a widely understood abstraction.

### Reasons to **not** adopt Arrow (chosen position)

- **Dependency weight:** Arrow consists of multiple artifacts (`arrow-core`, `arrow-fx-coroutines`,
  etc.). For a solo-developer project without coroutine-heavy code, the added dependency is
  disproportionate to the benefit.
- **Learning curve:** The `raise` DSL and functional combinators are unfamiliar to developers
  accustomed to imperative Kotlin; AI coding agents also produce more reliable output with simpler,
  idiomatic Kotlin patterns.
- **Sufficient without Arrow:** The `DomainResult<T>` + `DomainError` design covers all required
  use cases with a handful of lines. The `map` helper and `when` pattern matching in Kotlin already
  provide readable, type-safe composition.
- **Interoperability:** Mixing `Either` with Jakarta EE / Quarkus CDI and `@Inject` requires extra
  care; a custom sealed class has no such friction.

**Decision:** Do not adopt Arrow. Use the custom `DomainResult<T>` / `DomainError` design
described above. See [ADR-0006](../adr/0006-error-handling-concept.md) for the formal record.

---

## Error Code Registry

| Prefix   | Domain Area           | Example codes                          |
|----------|-----------------------|----------------------------------------|
| `AUTH`   | Authentication/login  | AUTH-001, AUTH-002, AUTH-003           |
| `USER`   | User management       | USER-001                               |
| `TOKEN`  | Token en/decryption   | TOKEN-001, TOKEN-002, TOKEN-003        |
| `SPOTIFY`| Spotify API calls     | SPOTIFY-001, SPOTIFY-002               |

New prefixes are registered here before implementation. The table is the single source of truth
for prefix assignments.

---

## Migration Plan

1. Add `DomainError` sealed interface and initial enum classes to `domain-api`.
2. Add `DomainResult<T>` sealed class to `domain-api`.
3. Replace `LoginResult` with `typealias LoginResult = DomainResult<UserId>` (or inline usage).
4. Update `LoginServiceAdapter` to use `DomainResult` and `AuthError`.
5. Update `SpotifyAuthPort` and `SpotifyAuthAdapter` to return `DomainResult<T>`.
6. Update `TokenEncryptionPort` and its adapter to return `DomainResult<T>`.
7. Update `OAuthResource` to map `DomainResult.Failure` to HTTP error responses using `error.code`.
8. Add a unit test that asserts all `DomainError` codes are unique across the codebase.
