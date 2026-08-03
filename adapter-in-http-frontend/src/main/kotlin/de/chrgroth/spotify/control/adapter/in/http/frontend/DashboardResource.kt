package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.DashboardStats
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
import jakarta.ws.rs.core.MediaType

@Path("/dashboard")
@ApplicationScoped
@Suppress("Unused")
class DashboardResource(
  @param:Location("dashboard.html")
  private val dashboardTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val dashboard: DashboardPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance = httpResponseMetrics.timed("page.dashboard.view") { details ->
    val displayName = details.detail("dashboard.view.display-name") { userProfile.getDisplayName() ?: securityIdentity.principal.name }
    val stats = dashboard.getStats()
    dashboardTemplate
      .data("displayName", displayName)
      .data("stats", stats)
  }

  @GET
  @Path("/snippets/playback-histogram")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaybackHistogram(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.playback-histogram") {
    val stats = dashboard.getPlaybackStats()
    dashboardTemplate.getFragment("snippet_playback_histogram").data("stats", stats)
  }

  @GET
  @Path("/snippets/recently-played")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetRecentlyPlayed(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.recently-played") {
    val stats = dashboard.getRecentlyPlayed()
    dashboardTemplate.getFragment("snippet_recently_played").data("stats", stats)
  }

  @GET
  @Path("/snippets/listening-stats")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetListeningStats(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.listening-stats") {
    val stats = dashboard.getListeningStats()
    dashboardTemplate.getFragment("snippet_listening_stats").data("stats", stats)
  }

}
