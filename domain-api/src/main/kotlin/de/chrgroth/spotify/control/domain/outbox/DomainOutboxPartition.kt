package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxPartition
import java.time.Duration

sealed interface DomainOutboxPartition : OutboxPartition {
    data object ToSpotify : DomainOutboxPartition {
        override val key = "to-spotify"
        override val throttleInterval: Duration = Duration.ofSeconds(5)
    }

    data object ToSpotifyPlayback : DomainOutboxPartition {
        override val key = "to-spotify-playback"
        override val pauseOnRateLimit = false
    }

    data object Domain : DomainOutboxPartition {
        override val key = "domain"
        override val pauseOnRateLimit = false
    }

    companion object {
        val all: List<DomainOutboxPartition> = listOf(ToSpotify, ToSpotifyPlayback, Domain)
    }
}
