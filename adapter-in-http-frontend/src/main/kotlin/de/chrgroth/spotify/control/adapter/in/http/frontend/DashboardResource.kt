package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
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
    Templates.dashboard(displayName, stats)
  }

  @GET
  @Path("/snippets/playback-histogram")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaybackHistogram(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.playback-histogram") {
    Templates.`dashboard$snippet_playback_histogram`(dashboard.getPlaybackStats())
  }

  @GET
  @Path("/snippets/recently-played")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetRecentlyPlayed(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.recently-played") {
    Templates.`dashboard$snippet_recently_played`(dashboard.getRecentlyPlayed())
  }

  @GET
  @Path("/snippets/listening-stats")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetListeningStats(): TemplateInstance = httpResponseMetrics.timed("fragment.dashboard.listening-stats") {
    Templates.`dashboard$snippet_listening_stats`(dashboard.getListeningStats())
  }

}
