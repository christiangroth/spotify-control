package de.chrgroth.spotify.control.outbox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RetryPolicy(
    val maxAttempts: Int = 5,
    val backoff: List<Duration> = listOf(5.seconds, 10.seconds, 30.seconds, 60.seconds),
) {
    fun nextRetryDelayOrNull(attempts: Int): Duration? =
        if (attempts >= maxAttempts) null else backoff.getOrElse(attempts - 1) { backoff.last() }
}
