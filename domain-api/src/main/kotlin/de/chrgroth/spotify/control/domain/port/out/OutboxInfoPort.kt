package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats

interface OutboxInfoPort {
    fun getPartitionStats(): List<OutboxPartitionStats>
}
