package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.metrics.HttpResponseMetrics
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackEventViewerPort
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
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Path("/playback/events")
@ApplicationScoped
@Suppress("Unused")
class PlaybackEventViewerResource(
  @param:Location("playback-event-viewer.html")
  private val template: Template,
  private val playbackEventViewer: PlaybackEventViewerPort,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(@QueryParam("date") dateParam: String?): TemplateInstance =
    httpResponseMetrics.timed("page.playback.event-viewer") {
      val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
      val requestedDate = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
      val date = if (requestedDate > today) today else requestedDate

      val result = playbackEventViewer.getEvents(date)

      template
        .data("result", result)
        .data("prevDate", date.minus(DatePeriod(days = 1)))
        .data("nextDate", date.plus(DatePeriod(days = 1)))
        .data("today", today)
    }
}
