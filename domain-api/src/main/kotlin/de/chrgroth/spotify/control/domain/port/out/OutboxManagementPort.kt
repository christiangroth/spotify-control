package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.OutboxTask

interface OutboxManagementPort {
    fun getPartitionStats(): List<OutboxPartitionStats>
    fun getTasksByPartition(partitionKey: String): List<OutboxTask>
    fun activate(partitionKey: String): Boolean
    fun deleteByEventTypes(eventTypeKeys: List<String>)
}
