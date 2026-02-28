package de.chrgroth.spotify.control.util.outbox

interface OutboxPayload {
    fun deduplicationKey(): String
}
