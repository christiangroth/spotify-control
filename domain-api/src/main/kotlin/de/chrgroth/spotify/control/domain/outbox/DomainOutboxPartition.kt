package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxPartition

sealed interface DomainOutboxPartition : OutboxPartition {
    data object ToSpotify : DomainOutboxPartition {
        override val key = "to-spotify"
    }

    data object ToSpotifyRecentlyPlayed : DomainOutboxPartition {
        override val key = "to-spotify-recently-played"
        override val pauseOnRateLimit = false
    }

    companion object {
        val all: List<DomainOutboxPartition> = listOf(ToSpotify, ToSpotifyRecentlyPlayed)
    }
}
