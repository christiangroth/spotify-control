package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionActivatedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionPausedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskDispatchedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskEnqueuedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskFailedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskRescheduledEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskRetryScheduledEvent
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPartitionObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskCountObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskFailedObserver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance

@ApplicationScoped
@Suppress("Unused")
class OutboxPartitionEventAdapter(
  @param:Any private val partitionObservers: Instance<OutboxPartitionObserver>,
  @param:Any private val taskCountObservers: Instance<OutboxTaskCountObserver>,
  @param:Any private val taskFailedObservers: Instance<OutboxTaskFailedObserver>,
) {

  // the outbox library fires all of these events via Event.fireAsync(), which the CDI spec only delivers to @ObservesAsync observers
  fun onPartitionPaused(@ObservesAsync event: OutboxPartitionPausedEvent) {
    val reason = event.reason?.takeIf { it.isNotBlank() } ?: "unknown"
    partitionObservers.forEach { it.onPartitionPaused(event.partition.key, reason) }
  }

  fun onPartitionActivated(@ObservesAsync event: OutboxPartitionActivatedEvent) {
    partitionObservers.forEach { it.onPartitionActivated(event.partition.key) }
  }

  @Suppress("UnusedParameter")
  fun onTaskEnqueued(@ObservesAsync event: OutboxTaskEnqueuedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskDispatched(@ObservesAsync event: OutboxTaskDispatchedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  fun onTaskFailed(@ObservesAsync event: OutboxTaskFailedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
    taskFailedObservers.forEach { it.onTaskFailed(event.partition.key, event.eventType) }
  }

  @Suppress("UnusedParameter")
  fun onTaskRescheduled(@ObservesAsync event: OutboxTaskRescheduledEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskRetryScheduled(@ObservesAsync event: OutboxTaskRetryScheduledEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }
}
