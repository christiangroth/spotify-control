package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Path("/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackResource(
  @param:Location("playback.html")
  private val playbackTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val dashboard: DashboardPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(@QueryParam("date") dateParam: String?): TemplateInstance = httpResponseMetrics.timed("page.playback.view") { details ->
    val displayName = details.detail("playback.view.display-name") { userProfile.getDisplayName() ?: securityIdentity.principal.name }
    val stats = dashboard.getStats()
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val requestedDate = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val eventsDate = requestedDate?.let { if (it > today) today else it }
    playbackTemplate
      .data("displayName", displayName)
      .data("stats", stats)
      .data("eventsDate", eventsDate)
  }
}
