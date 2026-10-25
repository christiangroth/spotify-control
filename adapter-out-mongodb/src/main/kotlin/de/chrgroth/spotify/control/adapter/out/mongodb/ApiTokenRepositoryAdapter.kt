package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.port.out.user.ApiTokenRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class ApiTokenRepositoryAdapter(
  private val apiTokenDocumentRepository: ApiTokenDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : ApiTokenRepositoryPort {

  override fun findAll(): List<ApiToken> =
    mongoQueryMetrics.timed("app_api_token.find-all") {
      apiTokenDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun findByHash(tokenHash: String): ApiToken? =
    mongoQueryMetrics.timed("app_api_token.find-by-hash") {
      apiTokenDocumentRepository.find(TOKEN_HASH_FIELD, tokenHash).firstResult()?.toDomain()
    }

  override fun insert(token: ApiToken) {
    mongoQueryMetrics.timed("app_api_token.insert") {
      apiTokenDocumentRepository.persist(token.toDocument())
    }
  }

  override fun setActive(id: ApiTokenId, active: Boolean): Boolean =
    mongoQueryMetrics.timed("app_api_token.set-active") {
      apiTokenDocumentRepository.mongoCollection()
        .updateOne(Filters.eq(ID_FIELD, id.value), Updates.set(ACTIVE_FIELD, active))
        .matchedCount > 0
    }

  override fun setScopes(id: ApiTokenId, scopes: Set<ApiTokenScope>): Boolean =
    mongoQueryMetrics.timed("app_api_token.set-scopes") {
      apiTokenDocumentRepository.mongoCollection()
        .updateOne(Filters.eq(ID_FIELD, id.value), Updates.set(SCOPES_FIELD, scopes.map { it.name }.sorted()))
        .matchedCount > 0
    }

  override fun updateLastUsedAt(id: ApiTokenId, lastUsedAt: Instant) {
    mongoQueryMetrics.timed("app_api_token.update-last-used-at") {
      apiTokenDocumentRepository.mongoCollection()
        .updateOne(Filters.eq(ID_FIELD, id.value), Updates.set(LAST_USED_AT_FIELD, lastUsedAt.toJavaInstant()))
    }
  }

  override fun delete(id: ApiTokenId): Boolean =
    mongoQueryMetrics.timed("app_api_token.delete") {
      apiTokenDocumentRepository.deleteById(id.value)
    }

  private fun ApiToken.toDocument() = ApiTokenDocument().also {
    it.id = id.value
    it.name = name
    it.tokenHash = tokenHash
    it.tokenPrefix = tokenPrefix
    it.scopes = scopes.map { scope -> scope.name }.sorted()
    it.active = active
    it.createdAt = createdAt.toJavaInstant()
    it.lastUsedAt = lastUsedAt?.toJavaInstant()
  }

  private fun ApiTokenDocument.toDomain() = ApiToken(
    id = ApiTokenId(id),
    name = name,
    tokenHash = tokenHash,
    tokenPrefix = tokenPrefix,
    // Unknown scope names (e.g. from a removed scope) are dropped instead of failing authentication.
    scopes = scopes.mapNotNull { name -> ApiTokenScope.entries.firstOrNull { it.name == name } }.toSet(),
    active = active,
    createdAt = createdAt.toKotlinInstant(),
    lastUsedAt = lastUsedAt?.toKotlinInstant(),
  )

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val TOKEN_HASH_FIELD = "tokenHash"
    internal const val SCOPES_FIELD = "scopes"
    internal const val ACTIVE_FIELD = "active"
    internal const val LAST_USED_AT_FIELD = "lastUsedAt"
  }
}
