package de.chrgroth.spotify.control.domain.port.out

interface OutboxActivationPort {
    fun activate(partitionKey: String): Boolean
}
