package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/ui/dashboard/events")
@ApplicationScoped
@Suppress("Unused")
class DashboardSseResource(
    private val sseService: DashboardSseService,
) {

    @GET
    @Authenticated
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun events(): Multi<String> = sseService.stream()
}
