package de.chrgroth.spotify.control.domain.model

data class OutboxViewerPartition(
    val key: String,
    val tasks: List<OutboxTask>,
)
