package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.util.outbox.OutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority

sealed interface DomainOutboxEvent : OutboxEvent {
    val partition: DomainOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL
    fun toPayload(): String

    data class FetchRecentlyPlayed(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.ToSpotify
        override val priority = OutboxTaskPriority.HIGH
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "FetchRecentlyPlayed"
        }
    }

    data class UpdateUserProfile(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "UpdateUserProfile"
        }
    }

    data class SyncPlaylistInfo(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "SyncPlaylistInfo"
        }
    }

    data class SyncPlaylistData(val userId: UserId, val playlistId: String) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}:$playlistId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "${userId.value}:$playlistId"

        companion object {
            const val KEY = "SyncPlaylistData"
            fun fromPayload(payload: String): SyncPlaylistData {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncPlaylistData payload: $payload" }
                return SyncPlaylistData(UserId(payload.substring(0, colonIndex)), payload.substring(colonIndex + 1))
            }
        }
    }

    companion object {
        fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
            FetchRecentlyPlayed.KEY -> FetchRecentlyPlayed(UserId(payload))
            UpdateUserProfile.KEY -> UpdateUserProfile(UserId(payload))
            SyncPlaylistInfo.KEY -> SyncPlaylistInfo(UserId(payload))
            SyncPlaylistData.KEY -> SyncPlaylistData.fromPayload(payload)
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
