package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * Token API (see [ApiTokenAuthMechanism]): playlist check results and missing artists as JSON, plus enqueueing
 * playlist syncs and check runs. Fixing check violations (which modifies Spotify playlists) is deliberately not exposed.
 */
@Path("/api/v1/playlists")
@ApplicationScoped
@Suppress("Unused")
class ApiPlaylistResource(
  private val playlistCheckPort: PlaylistCheckPort,
  private val playlistSettings: PlaylistSettingsResource,
  private val playlistChecks: PlaylistChecksResource,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Path("/checks")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun checks(): Response = httpResponseMetrics.timed("rest.api.playlists.checks") {
    val dashboard = playlistCheckPort.getCheckDashboard()
    Response.ok(
      dashboard.checks.map { check ->
        mapOf(
          "checkId" to check.checkId,
          "checkName" to dashboard.displayNames[check.checkId],
          "playlistId" to check.playlistId.value,
          "playlistName" to dashboard.playlistNameById[check.playlistId.value],
          "lastCheck" to check.lastCheck.toString(),
          "succeeded" to check.succeeded,
          "fixable" to (check.checkId in dashboard.fixableCheckIds),
          "violations" to check.violations.map { mapOf("id" to it.id, "message" to it.message) },
        )
      },
    ).build()
  }

  @GET
  @Path("/{playlistId}/missing-artists")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun missingArtists(@PathParam("playlistId") playlistId: String): Response = playlistSettings.missingArtists(playlistId)

  @POST
  @Path("/sync")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun syncAll(): Response = playlistSettings.syncNow()

  @POST
  @Path("/{playlistId}/sync")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun syncPlaylist(@PathParam("playlistId") playlistId: String): Response = playlistSettings.syncPlaylist(playlistId)

  @POST
  @Path("/checks/trigger")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerChecks(): Response = playlistChecks.triggerChecks()

  @POST
  @Path("/checks/{checkType}/trigger")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerCheck(@PathParam("checkType") checkType: String): Response = playlistChecks.triggerCheck(checkType)
}
