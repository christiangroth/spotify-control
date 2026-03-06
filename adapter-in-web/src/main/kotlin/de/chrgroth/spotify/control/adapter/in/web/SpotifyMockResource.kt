package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.PathParam
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

    @GET
    @Path("/v1/me/playlists")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    fun playlists(): String = PLAYLISTS_RESPONSE

    @GET
    @Path("/v1/playlists/{playlistId}/items")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Suppress("UnusedParameter")
    fun playlistTracks(@PathParam("playlistId") playlistId: String): String = PLAYLIST_TRACKS_RESPONSE

    companion object {
        private val TOKEN_RESPONSE = """
            {"access_token":"mock-access-token","refresh_token":"mock-refresh-token","expires_in":3600,"token_type":"Bearer"}
        """.trimIndent()
        private val REFRESH_RESPONSE = """
            {"access_token":"mock-refreshed-access-token","expires_in":3600,"token_type":"Bearer"}
        """.trimIndent()
        private val RECENTLY_PLAYED_RESPONSE = """
            {"items":[
              {"track":{"id":"track-1","name":"Track One","type":"track","is_local":false,"artists":[{"id":"artist-1","name":"Artist One"}]},"played_at":"2024-01-01T12:00:00.000Z"},
              {"track":{"id":"episode-1","name":"Podcast Episode One","type":"episode"},"played_at":"2024-01-01T11:00:00.000Z"},
              {"track":{"id":"local-1","name":"Local Track","type":"track","is_local":true,"artists":[{"id":"","name":"Local Artist"}]},"played_at":"2024-01-01T10:00:00.000Z"}
            ],"next":null}
        """.trimIndent()
        private val PLAYLISTS_RESPONSE = """
            {"href":"","limit":50,"next":null,"offset":0,"previous":null,"total":1,"items":[
              {"id":"mock-playlist-1","name":"My Playlist","snapshot_id":"mock-snapshot-1","owner":{"id":"test-user-a"}}
            ]}
        """.trimIndent()
        private val PLAYLIST_TRACKS_RESPONSE = """
            {"href":"","snapshot_id":"mock-snapshot-1","limit":50,"next":null,"offset":0,"previous":null,"total":2,"items":[
              {"item":{"id":"track-1","name":"Track One","type":"track","artists":[{"id":"artist-1","name":"Artist One"}]}},
              {"item":{"id":"episode-1","name":"Podcast Episode One","type":"episode"}}
            ]}
        """.trimIndent()
    }
}
