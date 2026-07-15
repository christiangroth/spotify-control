package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.model.infra.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxStatsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboxMetricsTests {

  private val outboxStatsPort: OutboxStatsPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = OutboxMetrics(outboxStatsPort, meterRegistry)

  @Test
  fun `gauges expose pending counts per partition and event type`() {
    val partition = DomainOutboxPartition.Domain
    val eventType = DomainOutboxEvent.allKeys.first()
    every { outboxStatsPort.current() } returns listOf(
      OutboxPartitionStats(
        name = partition.key,
        status = "ACTIVE",
        documentCount = 3L,
        blockedUntil = null,
        eventTypeCounts = listOf(OutboxEventTypeCount(eventType = eventType, count = 3L)),
      ),
    )

    metrics.onStartup(StartupEvent())

    val partitionGauge = meterRegistry.find("outbox.partition.pending").tag("partition", partition.key).gauge()
    val eventTypeGauge = meterRegistry.find("outbox.event_type.pending").tag("eventType", eventType).gauge()

    assertThat(partitionGauge?.value()).isEqualTo(3.0)
    assertThat(eventTypeGauge?.value()).isEqualTo(3.0)
  }

  @Test
  fun `partition stats are shared across all gauge reads instead of re-queried per partition or event type`() {
    every { outboxStatsPort.current() } returns emptyList()

    metrics.onStartup(StartupEvent())
    meterRegistry.meters.forEach { meter -> meter.measure().forEach { it.value } }

    verify(exactly = 1) { outboxStatsPort.current() }
  }
}
