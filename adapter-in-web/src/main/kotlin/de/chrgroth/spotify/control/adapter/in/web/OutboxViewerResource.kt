package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/outbox-viewer")
@ApplicationScoped
@Suppress("Unused")
class OutboxViewerResource(
  @param:Location("outbox-viewer.html")
  private val template: Template,
  private val outboxViewer: OutboxViewerPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(): TemplateInstance = template.data("partitions", outboxViewer.getPartitions())

  @GET
  @Path("/snippets/tasks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetTasks(): TemplateInstance =
    template.getFragment("snippet_tasks").data("partitions", outboxViewer.getPartitions())

  @POST
  @Path("/wipe")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun wipe(): Response {
    outboxViewer.wipeAll()
    return Response.ok(mapOf("status" to "ok")).build()
  }
}
