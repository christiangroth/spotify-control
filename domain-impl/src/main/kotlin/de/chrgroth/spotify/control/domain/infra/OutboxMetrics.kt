package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

// registers eagerly on StartupEvent so these gauges are always visible, even before any outbox activity occurs.
// backed by the actual persisted document counts rather than an enqueued/processed counter diff, which drifts
// from reality across app restarts (counters reset, the persisted backlog does not).
// stats are refreshed on a background schedule rather than during gauge evaluation, so a slow/blocking outbox
// query can never delay the Prometheus scrape response itself (a scrape timeout drops the whole /q/metrics scrape,
// not just the affected gauges).
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxMetrics(
  private val outboxPort: OutboxPort,
  private val meterRegistry: MeterRegistry,
) {

  @Volatile
  private var cachedStats: List<OutboxPartitionStats> = emptyList()

  fun onStartup(@Observes event: StartupEvent) {
    refresh()

    DomainOutboxPartition.all.forEach { partition ->
      Gauge.builder("outbox.partition.pending", this) { pendingCountForPartition(partition.key).toDouble() }
        .tag("partition", partition.key)
        .description("Number of outbox events currently pending in this partition")
        .register(meterRegistry)
    }

    DomainOutboxEvent.allKeys.forEach { eventType ->
      Gauge.builder("outbox.event_type.pending", this) { pendingCountForEventType(eventType).toDouble() }
        .tag("eventType", eventType)
        .description("Number of outbox events of this type currently pending, across all partitions")
        .register(meterRegistry)
    }
  }

  @Scheduled(every = "15s")
  fun refresh() {
    try {
      cachedStats = outboxPort.getPartitionStats()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh outbox partition stats for metrics, keeping previous values" }
    }
  }

  private fun pendingCountForPartition(partitionKey: String): Long =
    cachedStats.firstOrNull { it.name == partitionKey }?.documentCount ?: 0L

  private fun pendingCountForEventType(eventType: String): Long =
    cachedStats.sumOf { partition -> partition.eventTypeCounts.firstOrNull { it.eventType == eventType }?.count ?: 0L }

  companion object : KLogging()
}
