package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPartitionObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskCountObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDetectedObserver
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class HealthSseAdapter : OutboxPartitionObserver, OutgoingRequestStatsObserver, OutboxTaskCountObserver, PlaybackDetectedObserver {

  private val emitters = CopyOnWriteArrayList<MultiEmitter<in String>>()

  fun stream(): Multi<String> = Multi.createFrom().emitter { emitter ->
    emitters.add(emitter)
    emitter.onTermination { emitters.remove(emitter) }
  }

  @Suppress("UnusedParameter")
  override fun onPartitionPaused(partitionKey: String, reason: String) = emitAll("refresh-outbox-partitions")

  @Suppress("UnusedParameter")
  override fun onPartitionActivated(partitionKey: String) = emitAll("refresh-outbox-partitions")

  override fun onRequestRecorded() = emitAll("refresh-outgoing-http-calls")

  override fun onOutboxTaskCountChanged() = emitAll("refresh-outbox-partitions")

  override fun onPlaybackDetected() = emitAll("refresh-playback-state")

  private fun emitAll(event: String) = emitters.forEach { runCatching { it.emit(event) } }
}
