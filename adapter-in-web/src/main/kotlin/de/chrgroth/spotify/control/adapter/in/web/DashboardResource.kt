package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
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
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance {
    val displayName = userProfile.getDisplayName() ?: securityIdentity.principal.name
    val stats = dashboard.getStats()
    return dashboardTemplate
      .data("displayName", displayName)
      .data("stats", stats)
  }

  @GET
  @Path("/snippets/playback-histogram")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaybackHistogram(): TemplateInstance {
    val stats = dashboard.getPlaybackStats()
    return dashboardTemplate.getFragment("snippet_playback_histogram").data("stats", stats)
  }

  @GET
  @Path("/snippets/recently-played")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetRecentlyPlayed(): TemplateInstance {
    val stats = dashboard.getRecentlyPlayed()
    return dashboardTemplate.getFragment("snippet_recently_played").data("stats", stats)
  }

  @GET
  @Path("/snippets/listening-stats")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetListeningStats(): TemplateInstance {
    val stats = dashboard.getListeningStats()
    return dashboardTemplate.getFragment("snippet_listening_stats").data("stats", stats)
  }

}
