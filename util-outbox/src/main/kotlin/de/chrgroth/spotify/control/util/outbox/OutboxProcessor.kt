package de.chrgroth.spotify.control.util.outbox

import arrow.core.Either
import java.time.Instant

class OutboxProcessor(
    private val repository: OutboxRepository,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {

    /**
     * Claims and dispatches the next available task for [partition].
     * Returns true if a task was claimed (regardless of dispatch outcome), false if no task was available.
     */
    fun processNext(
        partition: OutboxPartition,
        dispatch: (OutboxTask) -> Either<OutboxError, Unit>,
    ): Boolean {
        val task = repository.claim(partition) ?: return false

        return when (val result = dispatch(task)) {
            is Either.Right -> {
                repository.complete(task)
                true
            }
            is Either.Left -> {
                val newAttempts = task.attempts + 1
                if (newAttempts >= retryPolicy.maxAttempts) {
                    repository.fail(task, result.value.message, null)
                } else {
                    val delay = retryPolicy.backoff.getOrElse(task.attempts) { retryPolicy.backoff.last() }
                    val nextRetryAt = Instant.now().plus(delay)
                    repository.fail(task, result.value.message, nextRetryAt)
                }
                true
            }
        }
    }
}
