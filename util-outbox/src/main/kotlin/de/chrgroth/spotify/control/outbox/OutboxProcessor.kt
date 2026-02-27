package de.chrgroth.spotify.control.outbox

import kotlin.time.toKotlinInstant
import mu.KLogging

class OutboxProcessor(
    private val repository: OutboxRepository,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {

    fun processNext(
        partition: String,
        dispatch: (OutboxTask) -> Result<Unit>,
    ): Boolean {
        val task = repository.claim(partition) ?: return false
        logger.debug { "Processing outbox task: id=${task.id} eventType=${task.eventType} attempt=${task.attempts + 1}" }
        dispatch(task).fold(
            onFailure = { ex ->
                val newAttempts = task.attempts + 1
                val delay = retryPolicy.nextRetryDelayOrNull(newAttempts)
                val nextRetryAt = delay?.let { java.time.Instant.now().toKotlinInstant() + it }
                repository.fail(task, ex.message ?: "Unknown error", nextRetryAt)
                logger.warn { "Outbox task failed: id=${task.id} attempt=$newAttempts nextRetryAt=$nextRetryAt error=${ex.message}" }
            },
            onSuccess = {
                repository.complete(task)
            },
        )
        return true
    }

    companion object : KLogging()
}
