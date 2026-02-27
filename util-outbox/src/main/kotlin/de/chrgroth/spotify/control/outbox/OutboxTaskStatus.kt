package de.chrgroth.spotify.control.outbox

enum class OutboxTaskStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED,
    ;
}
