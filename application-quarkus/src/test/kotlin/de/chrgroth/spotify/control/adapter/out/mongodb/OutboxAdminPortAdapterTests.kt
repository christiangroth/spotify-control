package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.MongoClient
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class OutboxAdminPortAdapterTests {

  @Inject
  lateinit var outbox: OutboxPort

  @Inject
  lateinit var outboxAdmin: OutboxAdminPort

  @Inject
  lateinit var mongoClient: MongoClient

  @ConfigProperty(name = "quarkus.mongodb.database")
  lateinit var databaseName: String

  @Test
  fun `wipeAll removes all enqueued outbox tasks and partition documents`() {
    outbox.enqueue(DomainOutboxEvent.FetchPlaybackData())

    outboxAdmin.wipeAll()

    val stats = outbox.getPartitionStats()
    assertThat(stats).allSatisfy { assertThat(it.documentCount).isZero() }
    assertThat(outbox.getTasksByPartition(DomainOutboxPartition.ToSpotifyPlayback.key)).isEmpty()
  }

  @Test
  fun `requeueStuckTasks clears only overdue pending retries and leaves other tasks untouched`() {
    val partition = DomainOutboxPartition.ToSpotifyPlayback.key
    val now = Instant.now()
    insertTask(partition = partition, status = "PENDING", nextRetryAt = now.minusSeconds(60))
    val freshTask = insertTask(partition = partition, status = "PENDING", nextRetryAt = null)
    val futureRetryTask = insertTask(partition = partition, status = "PENDING", nextRetryAt = now.plusSeconds(60))
    val processingTask = insertTask(partition = partition, status = "PROCESSING", nextRetryAt = now.minusSeconds(60))

    val clearedCount = outboxAdmin.requeueStuckTasks(partition)

    assertThat(clearedCount).isEqualTo(1)
    val remainingIds = outbox.getTasksByPartition(partition).map { it.deduplicationKey }
    assertThat(remainingIds).containsExactlyInAnyOrder(freshTask, futureRetryTask, processingTask)
  }

  private fun insertTask(partition: String, status: String, nextRetryAt: Instant?): String {
    val deduplicationKey = UUID.randomUUID().toString()
    val now = Instant.now()
    mongoClient.getDatabase(databaseName).getCollection("outbox").insertOne(
      Document().apply {
        put("_id", UUID.randomUUID().toString())
        put("partition", partition)
        put("eventType", DomainOutboxEvent.FetchPlaybackData.KEY)
        put("deduplicationKey", deduplicationKey)
        put("payload", "")
        put("status", status)
        put("attempts", 1)
        put("createdAt", now)
        put("updatedAt", now)
        put("nextRetryAt", nextRetryAt)
        put("priority", "HIGH")
        put("priorityOrder", 1)
        put("lastError", "Connection reset")
      },
    )
    return deduplicationKey
  }
}
