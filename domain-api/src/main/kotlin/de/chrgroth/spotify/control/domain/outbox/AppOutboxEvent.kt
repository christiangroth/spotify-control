package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority

sealed interface AppOutboxEvent : OutboxEvent {
    val partition: AppOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL
    fun toPayload(): String

    data class FetchRecentlyPlayedForUser(val userId: String) : AppOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$userId"
        override val partition = AppOutboxPartition.RecentlyPlayed
        override val priority = OutboxTaskPriority.HIGH
        override fun toPayload() = userId

        companion object {
            const val KEY = "FetchRecentlyPlayedForUser"
        }
    }

    data class UpdateUserProfileForUser(val userId: String) : AppOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$userId"
        override val partition = AppOutboxPartition.UserProfileUpdate
        override fun toPayload() = userId

        companion object {
            const val KEY = "UpdateUserProfileForUser"
        }
    }

    companion object {
        fun fromKey(key: String, payload: String): AppOutboxEvent = when (key) {
            FetchRecentlyPlayedForUser.KEY -> FetchRecentlyPlayedForUser(payload)
            UpdateUserProfileForUser.KEY -> UpdateUserProfileForUser(payload)
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
