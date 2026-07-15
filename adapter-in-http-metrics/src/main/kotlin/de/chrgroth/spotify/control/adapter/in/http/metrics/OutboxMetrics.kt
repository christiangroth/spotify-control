package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes

// registers eagerly on StartupEvent so these gauges are always visible, even before any outbox activity occurs.
// backed by the actual persisted document counts rather than an enqueued/processed counter diff, which drifts
// from reality across app restarts (counters reset, the persisted backlog does not).
// counts are read from OutboxStatsPort rather than queried here, so a slow/blocking outbox query can never
// delay the Prometheus scrape response itself. The read is shared via ScrapeSnapshot so all gauges below
// report values from the same snapshot instead of each independently re-fetching.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxMetrics(
  private val outboxStatsPort: OutboxStatsPort,
  private val meterRegistry: MeterRegistry,
) {

  private val snapshot = ScrapeSnapshot { outboxStatsPort.current() }

  fun onStartup(@Observes event: StartupEvent) {
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
    snapshot.current().firstOrNull { it.name == partitionKey }?.documentCount ?: 0L

  private fun pendingCountForEventType(eventType: String): Long =
    snapshot.current().sumOf { partition -> partition.eventTypeCounts.firstOrNull { it.eventType == eventType }?.count ?: 0L }
}
