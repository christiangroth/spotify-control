package de.chrgroth.spotify.control.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.model.user.CreatedApiToken

interface ApiTokenPort {
  fun listTokens(): List<ApiToken>
  fun createToken(name: String, scopes: Set<ApiTokenScope>): Either<DomainError, CreatedApiToken>
  fun setActive(id: ApiTokenId, active: Boolean): Either<DomainError, Unit>
  fun setScopes(id: ApiTokenId, scopes: Set<ApiTokenScope>): Either<DomainError, Unit>
  fun deleteToken(id: ApiTokenId): Either<DomainError, Unit>

  /** Returns the matching token if [plainToken] is known and active, recording its usage; null otherwise. */
  fun authenticate(plainToken: String): ApiToken?
}
