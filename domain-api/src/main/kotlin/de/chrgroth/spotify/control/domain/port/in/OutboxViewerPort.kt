package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.OutboxTask

interface OutboxViewerPort {
    fun getPartitions(): List<OutboxViewerPartition>
}

data class OutboxViewerPartition(
    val key: String,
    val tasks: List<OutboxTask>,
)
