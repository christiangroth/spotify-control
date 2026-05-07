package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/logs-viewer")
@ApplicationScoped
@Suppress("Unused")
class LogsViewerResource {

  @Inject
  @Location("logs-viewer.html")
  private lateinit var template: Template

  @Inject
  private lateinit var logsCollector: LogsCollector

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(): TemplateInstance = template.data("logs", logsCollector.getRecentLogs())
}
