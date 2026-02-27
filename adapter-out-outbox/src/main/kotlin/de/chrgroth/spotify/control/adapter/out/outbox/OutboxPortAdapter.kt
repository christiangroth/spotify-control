package de.chrgroth.spotify.control.adapter.out.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.outbox.AppOutboxEventType
import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.outbox.OutboxRepository
import de.chrgroth.spotify.control.outbox.OutboxWakeupService
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
class OutboxPortAdapter(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
    private val objectMapper: ObjectMapper,
) : OutboxPort {

    override fun enqueue(partition: AppOutboxPartition, eventType: AppOutboxEventType, payload: Any) {
        val payloadJson = objectMapper.writeValueAsString(payload)
        repository.enqueue(partition.key, eventType.key, payloadJson)
        wakeupService.signal(partition.key)
        logger.debug { "Enqueued outbox task and signalled: partition=${partition.key} eventType=${eventType.key}" }
    }

    companion object : KLogging()
}
