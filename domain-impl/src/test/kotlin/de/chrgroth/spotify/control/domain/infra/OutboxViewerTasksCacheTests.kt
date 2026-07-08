package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboxViewerTasksCacheTests {

  private val outbox: OutboxPort = mockk()
  private val cache = OutboxViewerTasksCache(outbox)

  private fun stubAllPartitionsEmpty() {
    DomainOutboxPartition.all.forEach { partition -> every { outbox.getTasksByPartition(partition.key) } returns emptyList() }
  }

  @Test
  fun `current is empty before the first refresh`() {
    assertThat(cache.current()).isEmpty()
  }

  @Test
  fun `refresh populates current with tasks per partition`() {
    stubAllPartitionsEmpty()
    val task = OutboxTask(
      eventType = "TYPE_A",
      deduplicationKey = "dedup-key",
      priority = "MEDIUM",
      status = "PENDING",
      attempts = 0,
      nextRetryAt = null,
      createdAt = Instant.fromEpochSeconds(0),
      lastError = null,
    )
    every { outbox.getTasksByPartition(DomainOutboxPartition.Domain.key) } returns listOf(task)

    cache.refresh()

    val domainPartition = cache.current().first { it.key == DomainOutboxPartition.Domain.key }
    assertThat(domainPartition.tasks).containsExactly(task)
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    stubAllPartitionsEmpty()
    cache.refresh()
    val cachedAfterFirstRefresh = cache.current()

    every { outbox.getTasksByPartition(any()) } throws IllegalStateException("outbox unreachable")
    cache.refresh()

    assertThat(cache.current()).isEqualTo(cachedAfterFirstRefresh)
  }

  @Test
  fun `current can be read repeatedly without re-querying the outbox port`() {
    stubAllPartitionsEmpty()
    cache.refresh()

    repeat(5) { cache.current() }

    DomainOutboxPartition.all.forEach { partition -> verify(exactly = 1) { outbox.getTasksByPartition(partition.key) } }
  }
}
