package de.chrgroth.spotify.control.domain.outbox

sealed interface OutboxError {
    val message: String

    data class DispatchFailed(override val message: String) : OutboxError
    data class Unexpected(override val message: String) : OutboxError
}
