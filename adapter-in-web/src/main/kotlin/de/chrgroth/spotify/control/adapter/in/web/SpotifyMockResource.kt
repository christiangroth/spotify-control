package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty

@Path("/mock/spotify")
@ApplicationScoped
@IfBuildProfile(anyOf = ["dev", "test"])
@Suppress("Unused")
class SpotifyMockResource {

    @ConfigProperty(name = "app.mock.spotify.user-id", defaultValue = "test-user-a")
    private lateinit var mockUserId: String

    @POST
    @Path("/api/token")
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    fun token(@jakarta.ws.rs.FormParam("grant_type") grantType: String?): String =
        when (grantType) {
            "refresh_token" -> REFRESH_RESPONSE
            else -> TOKEN_RESPONSE
        }

    @GET
    @Path("/v1/me")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    fun profile(): String =
        """{"id":"$mockUserId","display_name":"Mock User"}"""

    companion object {
        private const val TOKEN_RESPONSE =
            """{"access_token":"mock-access-token","refresh_token":"mock-refresh-token","expires_in":3600,"token_type":"Bearer"}"""
        private const val REFRESH_RESPONSE =
            """{"access_token":"mock-refreshed-access-token","expires_in":3600,"token_type":"Bearer"}"""
    }
}
