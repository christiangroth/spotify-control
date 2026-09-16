package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.port.`in`.infra.HealthPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
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
  private val health: HealthPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun health(): TemplateInstance = httpResponseMetrics.timed("page.health.view") { Templates.health(health.getStats()) }

  @GET
  @Path("/snippets/predicates")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPredicates(): TemplateInstance = httpResponseMetrics.timed("fragment.health.predicates") {
    Templates.`health$snippet_predicates`(HealthStats(predicateStats = health.getPredicateStats()))
  }

  @GET
  @Path("/snippets/cronjobs")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCronjobs(): TemplateInstance = httpResponseMetrics.timed("fragment.health.cronjobs") {
    Templates.`health$snippet_cronjobs`(HealthStats(cronjobStats = health.getCronjobStats()))
  }

  @GET
  @Path("/snippets/outgoing-http-calls")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutgoingHttpCalls(): TemplateInstance = httpResponseMetrics.timed("fragment.health.outgoing-http-calls") {
    Templates.`health$snippet_outgoing_http_calls`(HealthStats(outgoingRequestStats = health.getOutgoingRequestStats()))
  }

  @GET
  @Path("/snippets/outbox-partitions")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutboxPartitions(): TemplateInstance = httpResponseMetrics.timed("fragment.health.outbox-partitions") {
    Templates.`health$snippet_outbox_partitions`(HealthStats(outboxPartitions = health.getOutboxPartitions()))
  }

  @GET
  @Path("/snippets/mongodb-collections")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbCollections(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-collections") {
    Templates.`health$snippet_mongodb_collections`(HealthStats(mongoCollectionStats = health.getMongoCollectionStats()))
  }

  @GET
  @Path("/snippets/mongodb-queries")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbQueries(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-queries") {
    Templates.`health$snippet_mongodb_queries`(HealthStats(mongoQueryStats = health.getMongoQueryStats()))
  }

  @GET
  @Path("/snippets/navbar-outbox-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarOutboxStatus(): TemplateInstance = httpResponseMetrics.timed("fragment.health.navbar-outbox-status") {
    Templates.`health$snippet_navbar_outbox_status`(HealthStats(outboxPartitions = health.getOutboxPartitions()))
  }

  @GET
  @Path("/snippets/navbar-playback-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarPlaybackStatus(): TemplateInstance = httpResponseMetrics.timed("fragment.health.navbar-playback-status") {
    Templates.`health$snippet_navbar_playback_status`(HealthStats(predicateStats = health.getPredicateStats()))
  }
}
