package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo

@Path("/spotify-debug")
@ApplicationScoped
@Suppress("Unused")
class SpotifyDebugResource(
  @param:Location("spotify-debug.html")
  private val spotifyDebugTemplate: Template,
  private val catalogBrowser: CatalogBrowserPort,
  private val playlist: PlaylistPort,
  private val securityIdentity: SecurityIdentity,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): TemplateInstance = spotifyDebugTemplate.instance()

  @GET
  @Path("/api/artists")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun searchArtists(@QueryParam("filter") filter: String?): List<PickerOption> {
    if (filter.isNullOrBlank()) return emptyList()
    return catalogBrowser.getArtists(filter).map { PickerOption(it.artistId, it.artistName) }
  }

  @GET
  @Path("/api/artists/{artistId}/albums")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun listArtistAlbums(@PathParam("artistId") artistId: String): List<PickerOption> {
    return catalogBrowser.getArtistAlbums(artistId).map { PickerOption(it.albumId, it.title ?: UNTITLED_ALBUM) }
  }

  @GET
  @Path("/api/playlists")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun searchPlaylists(@QueryParam("filter") filter: String?): List<PickerOption> {
    if (filter.isNullOrBlank()) return emptyList()
    val userId = UserId(securityIdentity.principal.name)
    val filterLower = filter.lowercase()
    return playlist.getPlaylists(userId)
      .filter { it.name.lowercase().contains(filterLower) }
      .map { PickerOption(it.spotifyPlaylistId, it.name) }
  }

  @POST
  @Path("/execute/{operation}")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun execute(@PathParam("operation") operation: String, @Context uriInfo: UriInfo): Response {
    val params = uriInfo.queryParameters.entries.associate { (key, values) -> key to (values.firstOrNull() ?: "") }
    return Response.ok(
      mapOf(
        "operation" to operation,
        "params" to params,
        "status" to "dummy",
        "message" to "Dummy response - the actual Spotify API call for this operation is not implemented yet.",
      ),
    ).build()
  }

  data class PickerOption(val id: String, val name: String)

  companion object {
    private const val UNTITLED_ALBUM = "(untitled)"
  }
}
