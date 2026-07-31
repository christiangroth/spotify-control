package de.chrgroth.spotify.control.domain.port.out.infra

interface OutboxTaskFailedObserver {
  fun onTaskFailed(partitionKey: String, eventType: String)
}
