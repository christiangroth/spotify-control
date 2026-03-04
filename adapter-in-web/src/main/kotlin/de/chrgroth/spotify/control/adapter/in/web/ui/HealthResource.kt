package de.chrgroth.spotify.control.adapter.`in`.web.ui

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.DashboardStatsPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/ui/health")
@ApplicationScoped
@Suppress("Unused")
class HealthResource {

    @Inject
    @Location("ui/health.html")
    private lateinit var healthTemplate: Template

    @Inject
    private lateinit var securityIdentity: SecurityIdentity

    @Inject
    private lateinit var dashboardStats: DashboardStatsPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun health(): TemplateInstance {
        val userId = UserId(securityIdentity.principal.name)
        val stats = dashboardStats.getStats(userId)
        return healthTemplate.data("stats", stats)
    }

    @GET
    @Path("/snippets/outgoing-http-calls")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetOutgoingHttpCalls(): TemplateInstance {
        val userId = UserId(securityIdentity.principal.name)
        val stats = dashboardStats.getStats(userId)
        return healthTemplate.getFragment("snippet_outgoing_http_calls").data("stats", stats)
    }

    @GET
    @Path("/snippets/outbox-partitions")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetOutboxPartitions(): TemplateInstance {
        val userId = UserId(securityIdentity.principal.name)
        val stats = dashboardStats.getStats(userId)
        return healthTemplate.getFragment("snippet_outbox_partitions").data("stats", stats)
    }
}
