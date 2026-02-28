package de.chrgroth.spotify.control.util.outbox

data class OutboxError(
    val message: String,
    val cause: Throwable? = null,
)
