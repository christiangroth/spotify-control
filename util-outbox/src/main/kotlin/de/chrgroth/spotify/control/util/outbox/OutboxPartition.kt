package de.chrgroth.spotify.control.util.outbox

interface OutboxPartition {
    val key: String

    /**
     * Controls whether the partition is paused when a rate-limited response is received.
     * Set to `false` for partitions where processing must continue even under rate limiting
     * (e.g. to avoid missing time-sensitive data). Defaults to `true`.
     */
    val pauseOnRateLimit: Boolean get() = true
}
