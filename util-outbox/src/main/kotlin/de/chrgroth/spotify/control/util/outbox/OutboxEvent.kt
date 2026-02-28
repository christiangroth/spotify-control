package de.chrgroth.spotify.control.util.outbox

interface OutboxEvent {
    val key: String
    fun deduplicationKey(): String
}
