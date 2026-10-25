package de.chrgroth.spotify.control.domain.model.user

import kotlin.time.Instant

@JvmInline
value class ApiTokenId(val value: String)

/**
 * What an API token may do. Each REST endpoint that accepts token authentication declares exactly one
 * required scope; endpoints without such a declaration are never reachable with a token.
 */
enum class ApiTokenScope {
  /** Read-only access to application data (health, outbox, catalog, playback, stats, playlist checks). */
  READ,

  /** Read-only calls proxied to the Spotify Web API (consumes the Spotify rate limit). */
  SPOTIFY_READ,

  /** Enqueue syncs, polls, checks and requeues - idempotent actions that never delete data. */
  WRITE,
}

/**
 * A personal access token for CLI / agent access to the REST API. Only the SHA-256 hash of the secret is stored;
 * [tokenPrefix] holds the first characters of the secret so the token can be recognised in the UI.
 * Inactive tokens are kept but rejected, so access can be granted for a limited time without rotating the secret.
 */
data class ApiToken(
  val id: ApiTokenId,
  val name: String,
  val tokenHash: String,
  val tokenPrefix: String,
  val scopes: Set<ApiTokenScope>,
  val active: Boolean,
  val createdAt: Instant,
  val lastUsedAt: Instant?,
)

/** Result of creating a token: the plain secret is only available right after creation. */
data class CreatedApiToken(
  val token: ApiToken,
  val plainToken: String,
)
