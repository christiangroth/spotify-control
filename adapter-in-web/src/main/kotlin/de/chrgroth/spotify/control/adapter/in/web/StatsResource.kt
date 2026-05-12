package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/stats")
@ApplicationScoped
@Suppress("Unused")
class StatsResource(
  @param:Location("stats.html")
  private val statsTemplate: Template,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun stats(): TemplateInstance = statsTemplate.instance()
}
