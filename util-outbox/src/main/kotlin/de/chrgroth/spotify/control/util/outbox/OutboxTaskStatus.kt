package de.chrgroth.spotify.control.util.outbox

enum class OutboxTaskStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED,
}
