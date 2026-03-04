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

    @GET
    @Path("/v1/me/player/recently-played")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    fun recentlyPlayed(): String = RECENTLY_PLAYED_RESPONSE

    companion object {
        private const val TOKEN_RESPONSE =
            """{"access_token":"mock-access-token","refresh_token":"mock-refresh-token","expires_in":3600,"token_type":"Bearer"}"""
        private const val REFRESH_RESPONSE =
            """{"access_token":"mock-refreshed-access-token","expires_in":3600,"token_type":"Bearer"}"""
        private const val RECENTLY_PLAYED_RESPONSE =
            """{"items":[{"track":{"id":"track-1","name":"Track One","type":"track","artists":[{"id":"artist-1","name":"Artist One"}]},"played_at":"2024-01-01T12:00:00.000Z"},{"track":{"id":"episode-1","name":"Podcast Episode One","type":"episode"},"played_at":"2024-01-01T11:00:00.000Z"}]}"""
    }
}
