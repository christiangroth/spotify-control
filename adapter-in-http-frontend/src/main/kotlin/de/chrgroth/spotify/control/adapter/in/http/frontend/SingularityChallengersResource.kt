package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularityPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
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

@Path("/playlists/challengers")
@ApplicationScoped
@Suppress("Unused")
class SingularityChallengersResource(
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val singularityPort: SingularityPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun challengers(): TemplateInstance = httpResponseMetrics.timed("page.playlist.challengers-tab") {
    val displayName = userProfile.getDisplayName() ?: securityIdentity.principal.name
    Templates.`singularity-challengers`(displayName, singularityPort.getChallengerGroups())
  }

  @POST
  @Authenticated
  @Path("/{artistId}/accept/{trackId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun accept(@PathParam("artistId") artistId: String, @PathParam("trackId") trackId: String): Response =
    httpResponseMetrics.timed("rest.playlist.challenger-accept") {
      singularityPort.acceptChallenger(artistId, trackId).fold(
        ifLeft = { error -> Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to error.code)).build() },
        ifRight = { Response.ok(mapOf("status" to "ok")).build() },
      )
    }

  @POST
  @Authenticated
  @Path("/{artistId}/discard/{trackId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun discard(@PathParam("artistId") artistId: String, @PathParam("trackId") trackId: String): Response =
    httpResponseMetrics.timed("rest.playlist.challenger-discard") {
      singularityPort.discardChallenger(artistId, trackId).fold(
        ifLeft = { error -> Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to error.code)).build() },
        ifRight = { Response.ok(mapOf("status" to "ok")).build() },
      )
    }
}
