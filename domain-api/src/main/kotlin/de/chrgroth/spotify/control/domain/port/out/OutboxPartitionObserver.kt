package de.chrgroth.spotify.control.domain.port.out

interface OutboxPartitionObserver {
    fun onPartitionPaused(partitionKey: String, reason: String)
    fun onPartitionActivated(partitionKey: String)
}
