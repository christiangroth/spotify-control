package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxPartition

sealed interface AppOutboxPartition : OutboxPartition {
    data object RecentlyPlayed : AppOutboxPartition {
        override val key = "recently-played"
    }

    data object UserProfileUpdate : AppOutboxPartition {
        override val key = "user-profile-update"
    }

    companion object {
        val all: List<AppOutboxPartition> = listOf(RecentlyPlayed, UserProfileUpdate)
    }
}
