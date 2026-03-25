package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackSettingsResource {

  @Inject
  @Location("settings/playback.html")
  private lateinit var playbackTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var playback: PlaybackPort

  @Inject
  private lateinit var catalog: CatalogPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val user = userRepository.findById(userId)
    val limit = PAGE_SIZE
    val undecidedArtists = catalog.findArtistsByStatus(ArtistPlaybackProcessingStatus.UNDECIDED, 0, limit)
    val activeArtists = catalog.findArtistsByStatus(ArtistPlaybackProcessingStatus.ACTIVE, 0, limit)
    val inactiveArtists = catalog.findArtistsByStatus(ArtistPlaybackProcessingStatus.INACTIVE, 0, limit)
    val undecidedTotal = catalog.countArtistsByStatus(ArtistPlaybackProcessingStatus.UNDECIDED)
    val activeTotal = catalog.countArtistsByStatus(ArtistPlaybackProcessingStatus.ACTIVE)
    val inactiveTotal = catalog.countArtistsByStatus(ArtistPlaybackProcessingStatus.INACTIVE)
    return playbackTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("undecidedArtists", undecidedArtists)
      .data("activeArtists", activeArtists)
      .data("inactiveArtists", inactiveArtists)
      .data("undecidedTotal", undecidedTotal)
      .data("activeTotal", activeTotal)
      .data("inactiveTotal", inactiveTotal)
      .data("pageSize", limit)
      .data("artists", emptyList<AppArtist>())
      .data("status", "")
      .data("hasMore", false)
      .data("nextOffset", 0)
  }

  @GET
  @Path("/artists")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistsByStatus(
    @QueryParam("status") statusStr: String,
    @QueryParam("offset") offset: Int = 0,
  ): TemplateInstance {
    val status = ArtistPlaybackProcessingStatus.entries.find { it.name == statusStr }
    if (status == null) {
      logger.warn { "Unknown artist status requested: $statusStr" }
      return playbackTemplate.getFragment("fragment_artist_items")
        .data("artists", emptyList<AppArtist>())
        .data("status", "")
        .data("hasMore", false)
        .data("nextOffset", 0)
    }
    val limit = PAGE_SIZE
    val artists = catalog.findArtistsByStatus(status, offset, limit)
    val hasMore = artists.size == limit
    return playbackTemplate.getFragment("fragment_artist_items")
      .data("artists", artists)
      .data("status", statusStr)
      .data("hasMore", hasMore)
      .data("nextOffset", offset + limit)
  }

  @POST
  @Authenticated
  @Path("/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildPlaybackData(): Response {
    val userId = UserId(securityIdentity.principal.name)
    playback.enqueueRebuildPlaybackData(userId)
    return Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/sync-from-playlists")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncArtistPlaybackFromPlaylists(): Response {
    val userId = UserId(securityIdentity.principal.name)
    playback.syncArtistPlaybackFromPlaylists(userId)
    return Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/resync-catalog")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncCatalog(): Response {
    return catalog.resyncCatalog().fold(
      ifLeft = { error ->
        logger.error { "Catalog re-sync failed: ${error.code}" }
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/resync")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response {
    return catalog.resyncArtist(artistId).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for re-sync: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist re-sync failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @PUT
  @Authenticated
  @Path("/artists/{artistId}/status")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateArtistStatus(
    @PathParam("artistId") artistId: String,
    request: ArtistStatusRequest,
  ): Response {
    val userId = UserId(securityIdentity.principal.name)
    val status = ArtistPlaybackProcessingStatus.entries.find { it.name == request.status }
      ?: return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid artist status: ${request.status}"))
        .build()
    return catalog.updateArtistPlaybackProcessingStatus(artistId, status, userId).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for status update: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist status update failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "Update failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to status.name)).build() },
    )
  }

  data class ArtistStatusRequest(val status: String = "")

  companion object : KLogging() {
    private const val PAGE_SIZE = 50
  }
}
