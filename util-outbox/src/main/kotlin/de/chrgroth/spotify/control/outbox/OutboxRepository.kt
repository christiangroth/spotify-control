package de.chrgroth.spotify.control.outbox

import kotlin.time.Instant

interface OutboxRepository {
    fun enqueue(partition: String, eventType: String, payload: String)
    fun claim(partition: String): OutboxTask?
    fun complete(task: OutboxTask)
    fun fail(task: OutboxTask, error: String, nextRetryAt: Instant?)
    fun resetStaleProcessing()
}
