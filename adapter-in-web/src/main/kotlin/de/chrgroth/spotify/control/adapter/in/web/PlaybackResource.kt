package de.chrgroth.spotify.control.adapter.`in`.web

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

@Path("/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackResource(
  @param:Location("playback.html")
  private val playbackTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val dashboard: DashboardPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(): TemplateInstance {
    val displayName = userProfile.getDisplayName() ?: securityIdentity.principal.name
    val stats = dashboard.getPlaybackStats()
    return playbackTemplate
      .data("displayName", displayName)
      .data("stats", stats)
  }
}
