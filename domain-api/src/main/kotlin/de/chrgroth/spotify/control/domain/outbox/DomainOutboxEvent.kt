package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.outbox.OutboxEvent
import de.chrgroth.outbox.OutboxTaskPriority

sealed interface DomainOutboxEvent : OutboxEvent {
    val partition: DomainOutboxPartition
    val priority: OutboxTaskPriority get() = OutboxTaskPriority.NORMAL
    fun toPayload(): String

    data class FetchCurrentlyPlaying(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.ToSpotifyPlayback
        override val priority = OutboxTaskPriority.HIGH
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "FetchCurrentlyPlaying"
        }
    }

    data class FetchRecentlyPlayed(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.ToSpotifyPlayback
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

    data class RebuildPlaybackData(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.Domain
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "RebuildPlaybackData"
        }
    }

    data class AppendPlaybackData(val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}"
        override val partition = DomainOutboxPartition.Domain
        override fun toPayload() = userId.value

        companion object {
            const val KEY = "AppendPlaybackData"
        }
    }

    /**
     * Syncs genres and images for a single artist from the Spotify API and updates app_artist.
     * Deduplication is by artistId only (artist data is shared across users).
     * payload = "$artistId:${userId.value}"
     */
    data class SyncArtistDetails(val artistId: String, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$artistId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "$artistId:${userId.value}"

        companion object {
            const val KEY = "SyncArtistDetails"
            const val LEGACY_KEY = "EnrichArtistDetails"
            fun fromPayload(payload: String): SyncArtistDetails {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncArtistDetails payload: $payload" }
                return SyncArtistDetails(
                    artistId = payload.substring(0, colonIndex),
                    userId = UserId(payload.substring(colonIndex + 1)),
                )
            }
        }
    }

    /**
     * Syncs a single album by fetching all its tracks via GET /v1/albums/{id}.
     * All returned tracks are upserted. Enqueues SyncArtistDetails for all artists found.
     * payload = albumId
     */
    data class SyncAlbumDetails(val albumId: String) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$albumId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = albumId

        companion object {
            const val KEY = "SyncAlbumDetails"
            fun fromPayload(payload: String): SyncAlbumDetails = SyncAlbumDetails(payload)
        }
    }

    /**
     * Re-enqueues sync events for all known artists, tracks, and albums in the catalog
     * so that they are refreshed from Spotify.
     * Deduplication ensures only one instance is queued at a time.
     */
    data class ResyncCatalog(val placeholder: String = "") : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = KEY
        override val partition = DomainOutboxPartition.Domain
        override fun toPayload() = ""

        companion object {
            const val KEY = "ResyncCatalog"
        }
    }

    /**
     * Runs all playlist checks for a given user's playlist.
     * Triggered when playlist data changes (snapshotId changed).
     * payload = "${userId.value}:$playlistId"
     */
    data class RunPlaylistChecks(val userId: UserId, val playlistId: String) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${userId.value}:$playlistId"
        override val partition = DomainOutboxPartition.Domain
        override fun toPayload() = "${userId.value}:$playlistId"

        companion object {
            const val KEY = "RunPlaylistChecks"
            fun fromPayload(payload: String): RunPlaylistChecks {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid RunPlaylistChecks payload: $payload" }
                return RunPlaylistChecks(UserId(payload.substring(0, colonIndex)), payload.substring(colonIndex + 1))
            }
        }
    }

    companion object {
        @Suppress("CyclomaticComplexMethod")
        fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
            FetchCurrentlyPlaying.KEY -> FetchCurrentlyPlaying(UserId(payload))
            FetchRecentlyPlayed.KEY -> FetchRecentlyPlayed(UserId(payload))
            UpdateUserProfile.KEY -> UpdateUserProfile(UserId(payload))
            SyncPlaylistInfo.KEY -> SyncPlaylistInfo(UserId(payload))
            SyncPlaylistData.KEY -> SyncPlaylistData.fromPayload(payload)
            RebuildPlaybackData.KEY -> RebuildPlaybackData(UserId(payload))
            AppendPlaybackData.KEY -> AppendPlaybackData(UserId(payload))
            SyncArtistDetails.KEY, SyncArtistDetails.LEGACY_KEY -> SyncArtistDetails.fromPayload(payload)
            SyncAlbumDetails.KEY -> SyncAlbumDetails.fromPayload(payload)
            ResyncCatalog.KEY -> ResyncCatalog()
            RunPlaylistChecks.KEY -> RunPlaylistChecks.fromPayload(payload)
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
