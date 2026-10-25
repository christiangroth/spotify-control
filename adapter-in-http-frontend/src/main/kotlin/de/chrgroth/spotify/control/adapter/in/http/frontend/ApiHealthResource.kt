package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.infra.HealthPort
import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * Token API (see [ApiTokenAuthMechanism]): technical health, outbox state and token self-information as JSON.
 */
@Path("/api/v1")
@ApplicationScoped
@Suppress("Unused")
class ApiHealthResource(
  private val identity: SecurityIdentity,
  private val health: HealthPort,
  private val outboxViewer: OutboxViewerPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Path("/token")
  @RolesAllowed(ApiTokenRoles.READ, ApiTokenRoles.SPOTIFY_READ, ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun token(): Response = httpResponseMetrics.timed("rest.api.token.info") {
    Response.ok(
      mapOf(
        "name" to identity.principal.name,
        "scopes" to identity.roles.sorted(),
      ),
    ).build()
  }

  @GET
  @Path("/health")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun health(): Response = httpResponseMetrics.timed("rest.api.health") {
    Response.ok(
      mapOf(
        "predicates" to health.getPredicateStats().map {
          mapOf("name" to it.name, "active" to it.active, "lastCheck" to it.lastCheck?.toString())
        },
        "cronjobs" to health.getCronjobStats().map {
          mapOf("name" to it.simpleName, "schedule" to it.cronSchedule, "nextExecution" to it.nextExecution?.toString(), "running" to it.running)
        },
        "outgoingRequests" to health.getOutgoingRequestStats().map {
          mapOf("endpoint" to it.endpoint, "requestCountLast24h" to it.requestCountLast24h)
        },
        "outboxPartitions" to health.getOutboxPartitions().map { partition ->
          mapOf(
            "name" to partition.name,
            "status" to partition.status,
            "documentCount" to partition.documentCount,
            "blockedUntil" to partition.blockedUntil?.toString(),
            "eventTypeCounts" to partition.eventTypeCounts.associate { it.eventType to it.count },
          )
        },
        "mongoCollections" to health.getMongoCollectionStats().map {
          mapOf("name" to it.name, "documentCount" to it.documentCount, "sizeKb" to it.sizeKb)
        },
        "mongoQueries" to health.getMongoQueryStats().map {
          mapOf("name" to it.name, "executionCountLast24h" to it.executionCountLast24h, "slowQueryCount" to it.slowQueryCount)
        },
      ),
    ).build()
  }

  @GET
  @Path("/outbox")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun outbox(): Response = httpResponseMetrics.timed("rest.api.outbox") {
    Response.ok(
      outboxViewer.getPartitions().map { partition ->
        mapOf(
          "partition" to partition.key,
          "tasks" to partition.tasks.map { task ->
            mapOf(
              "eventType" to task.eventType,
              "deduplicationKey" to task.deduplicationKey,
              "priority" to task.priority,
              "status" to task.status,
              "attempts" to task.attempts,
              "nextRetryAt" to task.nextRetryAt?.toString(),
              "createdAt" to task.createdAt.toString(),
              "lastError" to task.lastError,
            )
          },
        )
      },
    ).build()
  }

  @POST
  @Path("/outbox/{partition}/requeue")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun requeue(@PathParam("partition") partition: String): Response = httpResponseMetrics.timed("rest.api.outbox.requeue") {
    Response.ok(mapOf("status" to "ok", "cleared" to outboxViewer.requeueStuckTasks(partition))).build()
  }
}
