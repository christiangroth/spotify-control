package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import kotlin.time.Instant

interface ApiTokenRepositoryPort {
  fun findAll(): List<ApiToken>
  fun findByHash(tokenHash: String): ApiToken?
  fun insert(token: ApiToken)
  fun setActive(id: ApiTokenId, active: Boolean): Boolean
  fun setScopes(id: ApiTokenId, scopes: Set<ApiTokenScope>): Boolean
  fun updateLastUsedAt(id: ApiTokenId, lastUsedAt: Instant)
  fun delete(id: ApiTokenId): Boolean
}
