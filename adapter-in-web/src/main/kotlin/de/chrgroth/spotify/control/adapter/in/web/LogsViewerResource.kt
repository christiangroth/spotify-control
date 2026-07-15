package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@Path("/logs-viewer")
@ApplicationScoped
@Suppress("Unused")
class LogsViewerResource(
  @param:Location("logs-viewer.html")
  private val template: Template,
  private val logsCollector: LogsCollector,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(@QueryParam("view") view: String?): TemplateInstance = httpResponseMetrics.timed("page.logs.view") {
    val logs = logsCollector.getRecentLogs()
    val viewMode = if (view?.equals("grouped", ignoreCase = true) == true) "grouped" else "chronological"
    template
      .data("logs", logs)
      .data("groups", groupLogsByClassAndType(logs))
      .data("viewMode", viewMode)
  }

  internal fun groupLogsByClassAndType(logs: List<LogUiEntry>): List<LogUiGroup> =
    logs
      .groupBy { it.className to it.level }
      .map { (key, entries) ->
        LogUiGroup(
          className = key.first,
          level = key.second,
          entries = entries.sortedByDescending { it.timestampEpochMillis },
        )
      }
      .sortedWith(
        compareBy<LogUiGroup>(
          { if (it.level == "ERROR") 0 else 1 },
          { -it.entries.size },
          { it.className },
        ),
      )
}

data class LogUiGroup(
  val className: String,
  val level: String,
  val entries: List<LogUiEntry>,
) {
  val count: Int
    get() = entries.size
}
