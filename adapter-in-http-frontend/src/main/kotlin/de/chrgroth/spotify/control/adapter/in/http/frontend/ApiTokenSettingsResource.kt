package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import arrow.core.Either
import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.ConfigMessages
import de.chrgroth.spotify.control.domain.error.ApiTokenError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.ApiToken
import de.chrgroth.spotify.control.domain.model.user.ApiTokenId
import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Management of API tokens from the config page. Only reachable with a cookie session: [ApiTokenAccessFilter] rejects
 * token-authenticated requests outside `/api/`, so a token can never create, widen or re-activate tokens.
 */
@Path("/settings/api-tokens")
@ApplicationScoped
@Suppress("Unused")
class ApiTokenSettingsResource(
  private val apiTokens: ApiTokenPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: ConfigMessages,
) {

  @POST
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun create(request: CreateApiTokenRequest): Response = httpResponseMetrics.timed("rest.api-token.create") {
    val scopes = parseScopes(request.scopes) ?: return@timed badRequest(messages.configApiTokenInvalidScopeError())
    apiTokens.createToken(request.name, scopes).fold(
      ifLeft = { errorResponse(it) },
      ifRight = { created ->
        Response.ok(mapOf("status" to "ok", "id" to created.token.id.value, "name" to created.token.name, "token" to created.plainToken)).build()
      },
    )
  }

  @PUT
  @Authenticated
  @Path("/{id}/active")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun setActive(@PathParam("id") id: String, request: SetActiveRequest): Response = httpResponseMetrics.timed("rest.api-token.set-active") {
    apiTokens.setActive(ApiTokenId(id), request.active).toResponse()
  }

  @PUT
  @Authenticated
  @Path("/{id}/scopes")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun setScopes(@PathParam("id") id: String, request: SetScopesRequest): Response = httpResponseMetrics.timed("rest.api-token.set-scopes") {
    val scopes = parseScopes(request.scopes) ?: return@timed badRequest(messages.configApiTokenInvalidScopeError())
    apiTokens.setScopes(ApiTokenId(id), scopes).toResponse()
  }

  @DELETE
  @Authenticated
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  fun delete(@PathParam("id") id: String): Response = httpResponseMetrics.timed("rest.api-token.delete") {
    apiTokens.deleteToken(ApiTokenId(id)).toResponse()
  }

  private fun parseScopes(names: List<String>): Set<ApiTokenScope>? =
    names.map { name -> ApiTokenScope.entries.firstOrNull { it.name == name } ?: return null }.toSet()

  private fun Either<DomainError, Unit>.toResponse(): Response =
    fold(ifLeft = { errorResponse(it) }, ifRight = { Response.ok(mapOf("status" to "ok")).build() })

  private fun errorResponse(error: DomainError): Response = when (error) {
    ApiTokenError.TOKEN_NOT_FOUND -> Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to messages.configApiTokenNotFoundError())).build()
    ApiTokenError.NAME_BLANK -> badRequest(messages.configApiTokenNameRequiredError())
    ApiTokenError.NO_SCOPES -> badRequest(messages.configApiTokenScopeRequiredError())
    else -> Response.serverError().entity(mapOf("error" to error.code)).build()
  }

  private fun badRequest(message: String): Response =
    Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

  data class CreateApiTokenRequest(val name: String = "", val scopes: List<String> = emptyList())
  data class SetActiveRequest(val active: Boolean = false)
  data class SetScopesRequest(val scopes: List<String> = emptyList())

  data class ApiTokenScopeOption(val name: String, val checked: Boolean)

  data class ApiTokenRow(
    val id: String,
    val name: String,
    val tokenPrefix: String,
    val active: Boolean,
    val scopes: List<ApiTokenScopeOption>,
    val createdAt: String,
    val lastUsedAt: String?,
  )

  companion object {
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun allScopeNames(): List<String> = ApiTokenScope.entries.map { it.name }

    fun toRow(token: ApiToken) = ApiTokenRow(
      id = token.id.value,
      name = token.name,
      tokenPrefix = token.tokenPrefix,
      active = token.active,
      scopes = ApiTokenScope.entries.map { ApiTokenScopeOption(it.name, it in token.scopes) },
      createdAt = format(token.createdAt),
      lastUsedAt = token.lastUsedAt?.let { format(it) },
    )

    private fun format(instant: Instant): String = DATE_TIME_FORMATTER.format(instant.toJavaInstant())
  }
}
