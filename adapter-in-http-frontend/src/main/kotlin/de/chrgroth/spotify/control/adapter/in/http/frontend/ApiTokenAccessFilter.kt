package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerRequestFilter

/**
 * Role names granted to API token identities, one per [ApiTokenScope]. Used with `@RolesAllowed` on the `/api/v1`
 * resources; cookie sessions carry no roles, so the token API is reachable with a token only.
 */
object ApiTokenRoles {
  const val READ = "READ"
  const val SPOTIFY_READ = "SPOTIFY_READ"
  const val WRITE = "WRITE"

  fun of(scope: ApiTokenScope): String = when (scope) {
    ApiTokenScope.READ -> READ
    ApiTokenScope.SPOTIFY_READ -> SPOTIFY_READ
    ApiTokenScope.WRITE -> WRITE
  }
}

/**
 * Default-deny for API tokens: a token-authenticated request is rejected unless it targets the dedicated token API
 * below [API_PATH_PREFIX], where each endpoint declares its required scope via `@RolesAllowed`. This keeps every UI
 * endpoint (including destructive ones like wipes and rebuilds) out of reach for tokens.
 */
@ApplicationScoped
@Suppress("Unused")
class ApiTokenAccessFilter(
  private val identity: SecurityIdentity,
) {

  @ServerRequestFilter(preMatching = true)
  fun filter(requestContext: ContainerRequestContext): Response? {
    if (identity.getAttribute<String>(ApiTokenAuthMechanism.API_TOKEN_ID_ATTRIBUTE) == null) return null
    val path = "/" + requestContext.uriInfo.path.trimStart('/')
    // Dot segments are rejected outright so no path can start with the prefix yet resolve elsewhere.
    if (path.startsWith(API_PATH_PREFIX) && !path.contains("..")) return null
    return Response.status(Response.Status.FORBIDDEN)
      .type(MediaType.APPLICATION_JSON)
      .entity(mapOf("error" to "Endpoint is not available for API token access"))
      .build()
  }

  companion object {
    const val API_PATH_PREFIX = "/api/"
  }
}
