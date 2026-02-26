package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.LoginResult
import de.chrgroth.spotify.control.domain.port.`in`.LoginServicePort
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Path("")
@ApplicationScoped
@Suppress("Unused")
class OAuthResource {

    @Inject
    private lateinit var loginService: LoginServicePort

    @Inject
    private lateinit var tokenEncryption: TokenEncryptionPort

    @ConfigProperty(name = "spotify.client-id")
    private lateinit var clientId: String

    @ConfigProperty(name = "app.oauth.redirect-uri")
    private lateinit var redirectUri: String

    @ConfigProperty(name = "spotify.accounts.base-url", defaultValue = "https://accounts.spotify.com")
    private lateinit var accountsBaseUrl: String

    private val stateStore = ConcurrentHashMap<String, Long>()

    private val scopes = "user-read-recently-played playlist-read-private playlist-modify-public playlist-modify-private user-read-private"

    @GET
    @PermitAll
    @Path("/oauth/authorize")
    fun authorize(): Response {
        val state = UUID.randomUUID().toString()
        stateStore[state] = System.currentTimeMillis()
        cleanExpiredStates()
        val authUrl = "$accountsBaseUrl/authorize" +
            "?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
            "&scope=${URLEncoder.encode(scopes, "UTF-8")}" +
            "&state=${URLEncoder.encode(state, "UTF-8")}"
        return Response.temporaryRedirect(URI.create(authUrl)).build()
    }

    @GET
    @PermitAll
    @Path("/oauth/callback")
    fun callback(
        @QueryParam("code") code: String?,
        @QueryParam("state") state: String?,
        @QueryParam("error") error: String?,
    ): Response {
        val validationError = validateCallbackParams(code, state, error)
        if (validationError != null) return Response.temporaryRedirect(URI.create("/?error=$validationError")).build()
        stateStore.remove(state!!)

        return when (val result = loginService.handleCallback(code!!)) {
            is LoginResult.Success -> {
                val cookieValue = tokenEncryption.encrypt(result.userId.value)
                Response.temporaryRedirect(URI.create("/ui/dashboard"))
                    .cookie(
                        NewCookie.Builder(SpotifyCookieAuthMechanism.COOKIE_NAME)
                            .value(cookieValue)
                            .path("/")
                            .httpOnly(true)
                            .sameSite(NewCookie.SameSite.LAX)
                            .build()
                    )
                    .build()
            }
            is LoginResult.Failure -> Response.temporaryRedirect(URI.create("/?error=${result.error}")).build()
        }
    }

    private fun validateCallbackParams(code: String?, state: String?, error: String?): String? = when {
        error != null -> "spotify_denied"
        code == null || state == null -> "invalid_request"
        !stateStore.containsKey(state) -> "state_mismatch"
        else -> null
    }

    @GET
    @PermitAll
    @Path("/logout")
    fun logout(): Response =
        Response.temporaryRedirect(URI.create("/"))
            .cookie(
                NewCookie.Builder(SpotifyCookieAuthMechanism.COOKIE_NAME)
                    .value("")
                    .path("/")
                    .maxAge(0)
                    .build()
            )
            .build()

    private fun cleanExpiredStates() {
        val expiry = System.currentTimeMillis() - STATE_TTL_MS
        stateStore.entries.removeIf { it.value < expiry }
    }

    companion object {
        private const val STATE_TTL_MS = 600_000L
    }
}

