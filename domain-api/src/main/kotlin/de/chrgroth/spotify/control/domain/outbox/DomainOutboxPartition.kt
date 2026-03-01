package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxPartition

sealed interface DomainOutboxPartition : OutboxPartition {
    data object ToSpotify : DomainOutboxPartition {
        override val key = "to-spotify"
    }

    companion object {
        val all: List<DomainOutboxPartition> = listOf(ToSpotify)
    }
}
