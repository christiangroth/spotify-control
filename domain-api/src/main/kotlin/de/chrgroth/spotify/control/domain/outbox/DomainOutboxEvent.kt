package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.util.outbox.OutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority

sealed interface DomainOutboxEvent : OutboxEvent {
    val partition: DomainOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL
    fun toPayload(): String

    data class FetchRecentlyPlayed(val userId: String) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$userId"
        override val partition = DomainOutboxPartition.ToSpotify
        override val priority = OutboxTaskPriority.HIGH
        override fun toPayload() = userId

        companion object {
            const val KEY = "FetchRecentlyPlayed"
        }
    }

    data class UpdateUserProfile(val userId: String) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$userId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = userId

        companion object {
            const val KEY = "UpdateUserProfile"
        }
    }

    companion object {
        fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
            FetchRecentlyPlayed.KEY -> FetchRecentlyPlayed(payload)
            UpdateUserProfile.KEY -> UpdateUserProfile(payload)
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
