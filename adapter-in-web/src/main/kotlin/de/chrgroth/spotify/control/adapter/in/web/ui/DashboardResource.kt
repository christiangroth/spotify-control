package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import jakarta.annotation.security.PermitAll
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

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance = dashboardTemplate.instance()
}
