package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/outbox-viewer")
@ApplicationScoped
@Suppress("Unused")
class OutboxViewerResource(
  private val outboxViewer: OutboxViewerPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(): TemplateInstance = httpResponseMetrics.timed("page.outbox.view") {
    Templates.`outbox-viewer`(outboxViewer.getPartitions())
  }

  @GET
  @Path("/snippets/tasks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetTasks(): TemplateInstance = httpResponseMetrics.timed("fragment.outbox.tasks") {
    Templates.`outbox-viewer$snippet_tasks`(outboxViewer.getPartitions())
  }

  @POST
  @Path("/wipe")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun wipe(): Response = httpResponseMetrics.timed("rest.outbox.wipe") {
    outboxViewer.wipeAll()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Path("/{partition}/requeue")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun requeue(@PathParam("partition") partition: String): Response = httpResponseMetrics.timed("rest.outbox.requeue") {
    val clearedCount = outboxViewer.requeueStuckTasks(partition)
    Response.ok(mapOf("status" to "ok", "cleared" to clearedCount)).build()
  }
}
