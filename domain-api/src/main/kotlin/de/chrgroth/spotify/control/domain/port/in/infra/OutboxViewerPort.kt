package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.OutboxViewerPartition

interface OutboxViewerPort {
    fun getPartitions(): List<OutboxViewerPartition>
}
