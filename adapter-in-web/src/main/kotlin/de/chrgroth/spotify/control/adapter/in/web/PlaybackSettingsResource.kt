package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

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
    val allArtists = catalog.findAllArtists().sortedBy { it.artistName }
    return playbackTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("undecidedArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.UNDECIDED })
      .data("activeArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.ACTIVE })
      .data("inactiveArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.INACTIVE })
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
          ArtistSettingsError.ARTIST_NOT_FOUND -> Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Artist not found: $artistId"))
            .build()
          else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(mapOf("error" to "Update failed: ${error.code}"))
            .build()
        }
      },
      ifRight = { Response.ok(mapOf("status" to status.name)).build() },
    )
  }

  data class ArtistStatusRequest(val status: String = "")
}
