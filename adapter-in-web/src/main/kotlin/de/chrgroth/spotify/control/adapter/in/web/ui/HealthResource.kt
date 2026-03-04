package de.chrgroth.spotify.control.adapter.`in`.web.ui

import de.chrgroth.spotify.control.domain.port.`in`.HealthStatsPort
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

@Path("/ui/health")
@ApplicationScoped
@Suppress("Unused")
class HealthResource {

    @Inject
    @Location("ui/health.html")
    private lateinit var healthTemplate: Template

    @Inject
    private lateinit var healthStats: HealthStatsPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun health(): TemplateInstance = healthTemplate.data("stats", healthStats.getStats())

    @GET
    @Path("/snippets/outgoing-http-calls")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetOutgoingHttpCalls(): TemplateInstance =
        healthTemplate.getFragment("snippet_outgoing_http_calls").data("stats", healthStats.getStats())

    @GET
    @Path("/snippets/outbox-partitions")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetOutboxPartitions(): TemplateInstance =
        healthTemplate.getFragment("snippet_outbox_partitions").data("stats", healthStats.getStats())

    @GET
    @Path("/snippets/mongodb-collections")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetMongoDbCollections(): TemplateInstance =
        healthTemplate.getFragment("snippet_mongodb_collections").data("stats", healthStats.getStats())

    @GET
    @Path("/snippets/mongodb-queries")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun snippetMongoDbQueries(): TemplateInstance =
        healthTemplate.getFragment("snippet_mongodb_queries").data("stats", healthStats.getStats())
}
