package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.jboss.resteasy.reactive.RestStreamElementType

@Path("/health/events")
@ApplicationScoped
@Suppress("Unused")
class HealthSseResource(
  private val sseAdapter: HealthSseAdapter,
) {

  @GET
  @Authenticated
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.TEXT_PLAIN)
  fun events(): Multi<String> = sseAdapter.stream()
}
