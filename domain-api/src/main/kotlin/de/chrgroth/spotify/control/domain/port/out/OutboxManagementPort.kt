package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats

interface OutboxManagementPort {
    fun getPartitionStats(): List<OutboxPartitionStats>
    fun activate(partitionKey: String): Boolean
    fun deleteByEventTypes(eventTypeKeys: List<String>)
}
