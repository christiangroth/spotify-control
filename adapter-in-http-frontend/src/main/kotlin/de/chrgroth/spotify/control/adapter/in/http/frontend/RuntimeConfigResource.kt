package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.ConfigMessages
import de.chrgroth.spotify.control.domain.port.`in`.user.RuntimeConfigPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/runtime-config")
@ApplicationScoped
@Suppress("Unused")
class RuntimeConfigResource(
  private val runtimeConfig: RuntimeConfigPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: ConfigMessages,
) {

  @POST
  @Authenticated
  @Path("/throttle-interval")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateThrottleInterval(request: ThrottleIntervalRequest): Response = httpResponseMetrics.timed("rest.config.throttle-interval-update") {
    if (request.intervalSeconds < 0) {
      return@timed Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to messages.configThrottleNonNegativeError()))
        .build()
    }
    runtimeConfig.setThrottleIntervalSeconds(request.intervalSeconds)
    logger.info { "Runtime throttle interval updated to ${request.intervalSeconds}s" }
    Response.ok(mapOf("status" to "ok", "intervalSeconds" to request.intervalSeconds)).build()
  }

  data class ThrottleIntervalRequest(val intervalSeconds: Long = 0)

  companion object : KLogging()
}
