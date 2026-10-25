package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.user.ApiTokenPort
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.infrastructure.Infrastructure
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import java.security.Principal
import java.util.Optional

/**
 * Authenticates requests carrying `Authorization: Bearer <api token>`. Runs before [SpotifyCookieAuthMechanism];
 * requests without a Bearer header fall through to the cookie session. Token scopes become roles (see [ApiTokenRoles]);
 * which endpoints a token may reach is enforced by [ApiTokenAccessFilter] and `@RolesAllowed` on the `/api/v1` resources.
 */
@ApplicationScoped
@Suppress("Unused")
class ApiTokenAuthMechanism(
  private val apiTokens: ApiTokenPort,
) : HttpAuthenticationMechanism {

  override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
    val plainToken = bearerToken(context) ?: return Uni.createFrom().optional(Optional.empty())
    // Token lookup hits MongoDB, so it must not run on the event loop.
    return Uni.createFrom().item { apiTokens.authenticate(plainToken) }
      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
      .map { token ->
        token?.let {
          QuarkusSecurityIdentity.builder()
            .setPrincipal(Principal { "$PRINCIPAL_PREFIX${token.name}" })
            .setAnonymous(false)
            .addRoles(token.scopes.map { ApiTokenRoles.of(it) }.toSet())
            .addAttribute(API_TOKEN_ID_ATTRIBUTE, token.id.value)
            .build()
        }
      }
  }

  override fun getChallenge(context: RoutingContext): Uni<ChallengeData?> =
    if (bearerToken(context) != null) {
      Uni.createFrom().item(ChallengeData(UNAUTHORIZED_STATUS, "WWW-Authenticate", "Bearer"))
    } else {
      // No Bearer header: let the cookie mechanism's challenge (redirect to login) apply.
      Uni.createFrom().nullItem()
    }

  override fun getPriority(): Int = HttpAuthenticationMechanism.DEFAULT_PRIORITY + 1

  private fun bearerToken(context: RoutingContext): String? =
    context.request().getHeader(AUTHORIZATION_HEADER)
      ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
      ?.substring(BEARER_PREFIX.length)
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

  companion object {
    const val API_TOKEN_ID_ATTRIBUTE = "api-token-id"
    private const val PRINCIPAL_PREFIX = "api-token:"
    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val BEARER_PREFIX = "Bearer "
    private const val UNAUTHORIZED_STATUS = 401
  }
}
