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

    @ConfigProperty(name = "app.mock.spotify.user-id")
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
    @Path("/v1/me/player/currently-playing")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    fun currentlyPlaying(): String = CURRENTLY_PLAYING_RESPONSE

    @GET
    @Path("/v1/me/player/recently-played")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    fun recentlyPlayed(): String = RECENTLY_PLAYED_RESPONSE

    @GET
    @Path("/v1/playlists/{playlistId}/items")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Suppress("UnusedParameter")
    fun playlistTracks(@PathParam("playlistId") playlistId: String): String = PLAYLIST_TRACKS_RESPONSE

    companion object {
        private const val TOKEN_RESPONSE =
            """{"access_token":"mock-access-token","refresh_token":"mock-refresh-token","expires_in":3600,"token_type":"Bearer"}"""
        private const val REFRESH_RESPONSE =
            """{"access_token":"mock-refreshed-access-token","expires_in":3600,"token_type":"Bearer"}"""
        private const val CURRENTLY_PLAYING_RESPONSE =
            """{"is_playing":true,"progress_ms":45000,"item":{"id":"track-2","name":"Track Two","type":"track","is_local":false,"duration_ms":200000,"artists":[{"id":"artist-2","name":"Artist Two"}]}}"""
        private const val RECENTLY_PLAYED_RESPONSE =
            """{"items":[{"track":{"id":"track-1","name":"Track One","type":"track","is_local":false,"artists":[{"id":"artist-1","name":"Artist One"}]},"played_at":"2024-01-01T12:00:00.000Z"},{"track":{"id":"episode-1","name":"Podcast Episode One","type":"episode"},"played_at":"2024-01-01T11:00:00.000Z"},{"track":{"id":"local-1","name":"Local Track","type":"track","is_local":true,"artists":[{"id":"","name":"Local Artist"}]},"played_at":"2024-01-01T10:00:00.000Z"}],"next":null}"""
        private const val PLAYLIST_TRACKS_RESPONSE =
            """{"snapshot_id":"mock-snapshot-1","items":[{"item":{"id":"track-1","name":"Track One","type":"track","artists":[{"id":"artist-1","name":"Artist One"}]}},{"item":{"id":"episode-1","name":"Podcast Episode One","type":"episode"}},{"item":null}],"next":null}"""
    }
}
