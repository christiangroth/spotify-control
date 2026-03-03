package de.chrgroth.spotify.control.adapter.`in`.web.ui

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardStatsPort
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

@Path("/ui/dashboard")
@ApplicationScoped
@Suppress("Unused")
class DashboardResource {

  @Inject
  @Location("ui/dashboard.html")
  private lateinit var dashboardTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var dashboardStats: DashboardStatsPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val user = userRepository.findById(userId)
    val stats = dashboardStats.getStats(userId)
    return dashboardTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("stats", stats)
  }
}
