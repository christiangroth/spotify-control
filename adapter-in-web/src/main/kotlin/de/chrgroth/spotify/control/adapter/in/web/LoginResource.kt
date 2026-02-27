package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.OAuthError
import de.chrgroth.spotify.control.domain.error.TokenError
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@Path("/")
@ApplicationScoped
@Suppress("Unused")
class LoginResource {

  @Inject
  @Location("login.html")
  private lateinit var loginTemplate: Template

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  fun index(@QueryParam("error") error: String?): TemplateInstance =
      loginTemplate.data("errorMessage", error?.let { errorMessage(it) })

  private fun errorMessage(code: String): String = when (code) {
      AuthError.USER_NOT_ALLOWED.code -> "You are not allowed to log in with this Spotify account."
      AuthError.TOKEN_EXCHANGE_FAILED.code -> "Could not exchange the authorisation code with Spotify. Please try again."
      AuthError.PROFILE_FETCH_FAILED.code -> "Could not retrieve your Spotify profile. Please try again."
      AuthError.TOKEN_REFRESH_FAILED.code -> "Could not refresh your access token. Please log in again."
      TokenError.ENCRYPTION_FAILED.code -> "An internal error occurred (encryption). Please contact support."
      TokenError.DECRYPTION_FAILED.code -> "Your session could not be verified. Please log in again."
      TokenError.INVALID_FORMAT.code -> "Your session is invalid. Please log in again."
      OAuthError.SPOTIFY_DENIED.code -> "Spotify login was denied. Please try again."
      OAuthError.INVALID_REQUEST.code -> "The login request was invalid. Please try again."
      OAuthError.STATE_MISMATCH.code -> "Login state validation failed. Please try again."
      else -> "An unexpected error occurred. Please try again."
  }
}
