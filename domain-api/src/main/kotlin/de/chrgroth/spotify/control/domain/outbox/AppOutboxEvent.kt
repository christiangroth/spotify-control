package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority

sealed interface AppOutboxEvent : OutboxEvent {
    val partition: AppOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL

    data object FetchRecentlyPlayed : AppOutboxEvent {
        override val key = "FetchRecentlyPlayed"
        override fun deduplicationKey() = "FetchRecentlyPlayed"
        override val partition = AppOutboxPartition.RecentlyPlayed
        override val priority = OutboxTaskPriority.HIGH
    }

    data object UpdateUserProfiles : AppOutboxEvent {
        override val key = "UpdateUserProfiles"
        override fun deduplicationKey() = "UpdateUserProfiles"
        override val partition = AppOutboxPartition.UserProfileUpdate
    }

    companion object {
        fun fromKey(key: String): AppOutboxEvent = when (key) {
            FetchRecentlyPlayed.key -> FetchRecentlyPlayed
            UpdateUserProfiles.key -> UpdateUserProfiles
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
