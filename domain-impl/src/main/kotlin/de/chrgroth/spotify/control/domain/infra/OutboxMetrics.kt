package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.Duration
import java.time.Instant

// registers eagerly on StartupEvent so these gauges are always visible, even before any outbox activity occurs.
// backed by the actual persisted document counts rather than an enqueued/processed counter diff, which drifts
// from reality across app restarts (counters reset, the persisted backlog does not).
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxMetrics(
  private val outboxPort: OutboxPort,
  private val meterRegistry: MeterRegistry,
) {

  // a single metrics scrape reads one gauge per partition and one per event type, each of which
  // needs the partition stats. without caching, that turns into one countByEventType query per
  // gauge per scrape; caching for a short window collapses those back down to a single query.
  @Volatile
  private var cachedStats: List<OutboxPartitionStats>? = null

  @Volatile
  private var cachedAt: Instant = Instant.EPOCH

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
    partitionStats().firstOrNull { it.name == partitionKey }?.documentCount ?: 0L

  private fun pendingCountForEventType(eventType: String): Long =
    partitionStats().sumOf { partition -> partition.eventTypeCounts.firstOrNull { it.eventType == eventType }?.count ?: 0L }

  private fun partitionStats(): List<OutboxPartitionStats> {
    val now = Instant.now()
    cachedStats?.takeIf { Duration.between(cachedAt, now) < CACHE_TTL }?.let { return it }

    return outboxPort.getPartitionStats().also {
      cachedStats = it
      cachedAt = now
    }
  }

  companion object {
    private val CACHE_TTL: Duration = Duration.ofSeconds(5)
  }
}
