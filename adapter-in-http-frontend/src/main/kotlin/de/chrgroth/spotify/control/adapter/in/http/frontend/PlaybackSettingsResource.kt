package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackSettingsResource(
  @param:Location("settings/playback.html")
  private val playbackTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val playback: PlaybackPort,
  private val catalog: CatalogPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(): TemplateInstance = httpResponseMetrics.timed("page.playback.settings") {
    val displayName = userProfile.getDisplayName() ?: securityIdentity.principal.name
    playbackTemplate
      .data("displayName", displayName)
  }

  @POST
  @Authenticated
  @Path("/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildPlaybackData(): Response = httpResponseMetrics.timed("rest.playback.rebuild") {
    playback.enqueueRebuildPlaybackData()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/resync-catalog")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncCatalog(): Response = httpResponseMetrics.timed("rest.catalog.resync") {
    catalog.enqueueResyncCatalog()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/resync")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-resync") {
      catalog.resyncArtist(artistId).fold(
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

  @POST
  @Authenticated
  @Path("/artists/{artistId}/set-sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun setArtistSync(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-set-sync") {
      handleArtistAction(artistId, "set-sync") { catalog.setArtistSync(artistId) }
    }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/set-shallow")
  @Produces(MediaType.APPLICATION_JSON)
  fun setArtistShallow(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-set-shallow") {
      handleArtistAction(artistId, "set-shallow") { catalog.setArtistShallow(artistId) }
    }

  private fun handleArtistAction(artistId: String, action: String, block: () -> Either<DomainError, Unit>): Response =
    block().fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for $action: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist $action failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "${action.replaceFirstChar { it.uppercase() }} failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )

  companion object : KLogging()
}
