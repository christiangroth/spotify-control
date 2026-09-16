package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.AppMessages
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.OAuthError
import de.chrgroth.spotify.control.domain.error.TokenError
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import org.eclipse.microprofile.config.inject.ConfigProperty

@Path("/")
@ApplicationScoped
@Suppress("Unused")
class LoginResource(
  private val securityIdentity: SecurityIdentity,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: AppMessages,
  @param:ConfigProperty(name = "quarkus.application.version")
  private val appBuildVersion: String,
) {

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  fun index(@QueryParam("error") error: String?): Response = httpResponseMetrics.timed("page.user.login") {
    if (!securityIdentity.isAnonymous) {
      Response.temporaryRedirect(URI.create("/dashboard")).build()
    } else {
      Response.ok(Templates.login(error?.let { errorMessage(it) }, appBuildVersion)).build()
    }
  }

  private fun errorMessage(code: String): String = when (code) {
      AuthError.ANOTHER_USER_ALREADY_REGISTERED.code -> messages.loginErrorAlreadyRegistered()
      AuthError.TOKEN_EXCHANGE_FAILED.code -> messages.loginErrorTokenExchangeFailed()
      AuthError.PROFILE_FETCH_FAILED.code -> messages.loginErrorProfileFetchFailed()
      AuthError.TOKEN_REFRESH_FAILED.code -> messages.loginErrorTokenRefreshFailed()
      TokenError.ENCRYPTION_FAILED.code -> messages.loginErrorEncryptionFailed()
      TokenError.DECRYPTION_FAILED.code -> messages.loginErrorSessionInvalidDecryption()
      TokenError.INVALID_FORMAT.code -> messages.loginErrorSessionInvalidFormat()
      OAuthError.SPOTIFY_DENIED.code -> messages.loginErrorSpotifyDenied()
      OAuthError.INVALID_REQUEST.code -> messages.loginErrorInvalidRequest()
      OAuthError.STATE_MISMATCH.code -> messages.loginErrorStateMismatch()
      else -> messages.loginErrorUnexpected()
  }
}
