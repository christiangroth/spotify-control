package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboxPartitionStatsCacheTests {

  private val outboxPort: OutboxPort = mockk()
  private val cache = OutboxPartitionStatsCache(outboxPort)

  @Test
  fun `current is empty before the first refresh`() {
    assertThat(cache.current()).isEmpty()
  }

  @Test
  fun `refresh populates current with the latest partition stats`() {
    val stats = listOf(OutboxPartitionStats(name = "partition", status = "ACTIVE", documentCount = 2L, blockedUntil = null, eventTypeCounts = emptyList()))
    every { outboxPort.getPartitionStats() } returns stats

    cache.refresh()

    assertThat(cache.current()).isEqualTo(stats)
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    val stats = listOf(OutboxPartitionStats(name = "partition", status = "ACTIVE", documentCount = 2L, blockedUntil = null, eventTypeCounts = emptyList()))
    every { outboxPort.getPartitionStats() } returns stats
    cache.refresh()

    every { outboxPort.getPartitionStats() } throws IllegalStateException("outbox unreachable")
    cache.refresh()

    assertThat(cache.current()).isEqualTo(stats)
  }

  @Test
  fun `current can be read repeatedly without re-querying the outbox port`() {
    every { outboxPort.getPartitionStats() } returns emptyList()
    cache.refresh()

    repeat(5) { cache.current() }

    verify(exactly = 1) { outboxPort.getPartitionStats() }
  }
}
