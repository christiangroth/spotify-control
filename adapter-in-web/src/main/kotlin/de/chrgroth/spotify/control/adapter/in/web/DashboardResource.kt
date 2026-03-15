package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/dashboard")
@ApplicationScoped
@Suppress("Unused")
class DashboardResource {

  @Inject
  @Location("dashboard.html")
  private lateinit var dashboardTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var dashboard: DashboardPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val user = userRepository.findById(userId)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("stats", stats)
  }

  @GET
  @Path("/snippets/playback-data")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaybackData(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_playback_data").data("stats", stats)
  }

  @GET
  @Path("/snippets/playback-histogram")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaybackHistogram(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_playback_histogram").data("stats", stats)
  }

  @GET
  @Path("/snippets/playlist-metadata")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaylistMetadata(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_playlist_metadata").data("stats", stats)
  }

  @GET
  @Path("/snippets/recently-played")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetRecentlyPlayed(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_recently_played").data("stats", stats)
  }

  @GET
  @Path("/snippets/listening-stats")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetListeningStats(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_listening_stats").data("stats", stats)
  }

  @GET
  @Path("/snippets/playlist-checks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPlaylistChecks(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_playlist_checks").data("stats", stats)
  }

  @GET
  @Path("/snippets/catalog-stats")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCatalogStats(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val stats = dashboard.getStats(userId)
    return dashboardTemplate.getFragment("snippet_catalog_stats").data("stats", stats)
  }
}
