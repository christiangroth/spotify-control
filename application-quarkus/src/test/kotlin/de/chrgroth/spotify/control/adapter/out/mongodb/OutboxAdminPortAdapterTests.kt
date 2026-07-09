package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class OutboxAdminPortAdapterTests {

  @Inject
  lateinit var outbox: OutboxPort

  @Inject
  lateinit var outboxAdmin: OutboxAdminPort

  @Test
  fun `wipeAll removes all enqueued outbox tasks and partition documents`() {
    outbox.enqueue(DomainOutboxEvent.FetchPlaybackData())

    outboxAdmin.wipeAll()

    val stats = outbox.getPartitionStats()
    assertThat(stats).allSatisfy { assertThat(it.documentCount).isZero() }
    assertThat(outbox.getTasksByPartition(DomainOutboxPartition.ToSpotifyPlayback.key)).isEmpty()
  }
}
