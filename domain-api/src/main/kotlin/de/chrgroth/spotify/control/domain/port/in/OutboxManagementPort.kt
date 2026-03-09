package de.chrgroth.spotify.control.domain.port.`in`

interface OutboxManagementPort {
    fun activatePartition(partitionKey: String): Boolean
}
