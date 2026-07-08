package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any outbox activity occurs.
// backed by the actual persisted document counts rather than an enqueued/processed counter diff, which drifts
// from reality across app restarts (counters reset, the persisted backlog does not).
// reads from OutboxPartitionStatsCache rather than querying the outbox library directly, so a slow/blocking
// outbox query can never delay the Prometheus scrape response itself (a scrape timeout drops the whole
// /q/metrics scrape, not just the affected gauges), and the same cached snapshot is shared with HealthService.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxMetrics(
  private val statsCache: OutboxPartitionStatsCache,
  private val meterRegistry: MeterRegistry,
) {

  fun onStartup(@Observes event: StartupEvent) {
    statsCache.refresh()

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

  private fun pendingCountForPartition(partitionKey: String): Long =
    statsCache.current().firstOrNull { it.name == partitionKey }?.documentCount ?: 0L

  private fun pendingCountForEventType(eventType: String): Long =
    statsCache.current().sumOf { partition -> partition.eventTypeCounts.firstOrNull { it.eventType == eventType }?.count ?: 0L }
}
