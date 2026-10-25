package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo

/**
 * Token API (see [ApiTokenAuthMechanism]): the read-only Spotify Web API operations of the Spotify debug page
 * (e.g. `current-user`, `currently-playing`, `playlist-tracks?playlistId=...`). Every call consumes Spotify rate limit,
 * hence the separate [ApiTokenRoles.SPOTIFY_READ] scope.
 */
@Path("/api/v1/spotify")
@ApplicationScoped
@Suppress("Unused")
class ApiSpotifyResource(
  private val spotifyDebug: SpotifyDebugResource,
) {

  @GET
  @Path("/{operation}")
  @RolesAllowed(ApiTokenRoles.SPOTIFY_READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun execute(@PathParam("operation") operation: String, @Context uriInfo: UriInfo): Response = spotifyDebug.execute(operation, uriInfo)
}
