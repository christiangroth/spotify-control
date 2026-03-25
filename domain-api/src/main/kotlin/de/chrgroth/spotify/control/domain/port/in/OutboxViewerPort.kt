package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.OutboxViewerPartition

interface OutboxViewerPort {
    fun getPartitions(): List<OutboxViewerPartition>
}
