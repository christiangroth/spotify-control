package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

@Path("/playlist-checks")
@ApplicationScoped
@Suppress("Unused")
class PlaylistChecksResource(
  @param:Location("playlist-checks.html")
  private val playlistChecksTemplate: Template,
  private val playlistCheckPort: PlaylistCheckPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playlistChecks(): TemplateInstance = httpResponseMetrics.timed("page.playlist.checks") {
    val dashboard = playlistCheckPort.getCheckDashboard()
    val groups = dashboard.checks
      .map { check ->
        PlaylistCheckRow(
          check = check,
          playlistName = dashboard.playlistNameById[check.playlistId.value] ?: check.playlistId.value,
          hasfix = dashboard.fixableCheckIds.contains(check.checkId.substringAfterLast(":")),
        )
      }
      .groupBy { it.check.checkId.substringAfterLast(":") }
      .map { (checkType, rows) ->
        val name = dashboard.displayNames[checkType] ?: checkType
        PlaylistCheckGroup(name, rows.sortedBy { it.playlistName })
      }
      .sortedBy { it.checkName }
    playlistChecksTemplate
      .data("displayName", dashboard.displayName)
      .data("groups", groups)
  }

  @POST
  @Authenticated
  @Path("/{playlistId}/fix/{checkType}")
  @Produces(MediaType.APPLICATION_JSON)
  fun runFix(
    @PathParam("playlistId") playlistId: String,
    @PathParam("checkType") checkType: String,
  ): Response = httpResponseMetrics.timed("rest.playlist.check-fix") {
    playlistCheckPort.runFix(playlistId, checkType).fold(
      ifLeft = { error -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(mapOf("error" to error.code)).build() },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  data class PlaylistCheckGroup(val checkName: String, val rows: List<PlaylistCheckRow>)

  data class PlaylistCheckRow(val check: AppPlaylistCheck, val playlistName: String, val hasfix: Boolean) {
    val checkDateFormatted: String get() = check.lastCheck
      .toJavaInstant()
      .atZone(ZoneId.systemDefault())
      .format(DATE_TIME_FORMATTER)
    val checkType: String get() = check.checkId.substringAfterLast(":")
    val playlistIdValue: String get() = check.playlistId.value

    companion object {
      private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
    }
  }
}
