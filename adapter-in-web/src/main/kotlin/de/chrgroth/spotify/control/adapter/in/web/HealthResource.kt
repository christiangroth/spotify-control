package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.infra.HealthPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/health")
@ApplicationScoped
@Suppress("Unused")
class HealthResource(
  @param:Location("health.html")
  private val healthTemplate: Template,
  private val health: HealthPort,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun health(): TemplateInstance = httpResponseMetrics.timed("page.health.view") { healthTemplate.data("stats", health.getStats()) }

  @GET
  @Path("/snippets/predicates")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPredicates(): TemplateInstance = httpResponseMetrics.timed("fragment.health.predicates") {
    healthTemplate.getFragment("snippet_predicates").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/cronjobs")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCronjobs(): TemplateInstance = httpResponseMetrics.timed("fragment.health.cronjobs") {
    healthTemplate.getFragment("snippet_cronjobs").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/outgoing-http-calls")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutgoingHttpCalls(): TemplateInstance = httpResponseMetrics.timed("fragment.health.outgoing-http-calls") {
    healthTemplate.getFragment("snippet_outgoing_http_calls").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/outbox-partitions")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutboxPartitions(): TemplateInstance = httpResponseMetrics.timed("fragment.health.outbox-partitions") {
    healthTemplate.getFragment("snippet_outbox_partitions").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-collections")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbCollections(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-collections") {
    healthTemplate.getFragment("snippet_mongodb_collections").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-queries")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbQueries(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-queries") {
    healthTemplate.getFragment("snippet_mongodb_queries").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/navbar-outbox-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarOutboxStatus(): TemplateInstance = httpResponseMetrics.timed("fragment.health.navbar-outbox-status") {
    healthTemplate.getFragment("snippet_navbar_outbox_status").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/navbar-playback-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarPlaybackStatus(): TemplateInstance = httpResponseMetrics.timed("fragment.health.navbar-playback-status") {
    healthTemplate.getFragment("snippet_navbar_playback_status").data("stats", health.getStats())
  }
}
