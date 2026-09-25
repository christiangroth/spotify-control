package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.SingularityMessages
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularityPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/playlists/singularity-artists")
@ApplicationScoped
@Suppress("Unused")
class SingularityArtistsResource(
  private val singularityPort: SingularityPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: SingularityMessages,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artists(): TemplateInstance = httpResponseMetrics.timed("page.playlist.singularity-artists") {
    Templates.`singularity-artists`(singularityPort.getArtistsForReview())
  }

  @POST
  @Authenticated
  @Path("/{artistId}/include")
  @Produces(MediaType.APPLICATION_JSON)
  fun include(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.playlist.singularity-artist-include") { setIncluded(artistId, true) }

  @POST
  @Authenticated
  @Path("/{artistId}/exclude")
  @Produces(MediaType.APPLICATION_JSON)
  fun exclude(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.playlist.singularity-artist-exclude") { setIncluded(artistId, false) }

  private fun setIncluded(artistId: String, included: Boolean): Response =
    singularityPort.setArtistIncluded(artistId, included).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for singularity-included update: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to messages.singularityArtistsErrorArtistNotFound(artistId))).build()
          }
          else -> {
            logger.error { "Failed to update singularity-included status for artist $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(mapOf("error" to messages.singularityArtistsErrorUpdateFailed(error.code))).build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )

  companion object : KLogging()
}
