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
     * Syncs track/album details for a single track from the Spotify API,
     * updates app_track with all sync fields, upserts app_album with the album data,
     * and enqueues SyncArtistDetails for all track artists not yet synced.
     * Deduplication is by trackId only (track data is shared across users).
     * payload = "$trackId:${userId.value}"
     */
    data class SyncTrackDetails(val trackId: String, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$trackId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "$trackId:${userId.value}"

        companion object {
            const val KEY = "SyncTrackDetails"
            const val LEGACY_KEY = "EnrichTrackDetails"
            fun fromPayload(payload: String): SyncTrackDetails {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncTrackDetails payload: $payload" }
                return SyncTrackDetails(
                    trackId = payload.substring(0, colonIndex),
                    userId = UserId(payload.substring(colonIndex + 1)),
                )
            }
        }
    }

    /**
     * Peeks up to 50 artist IDs from app_sync_pool and syncs them via the Spotify bulk artists endpoint.
     * Successfully synced IDs are removed from the pool; unsynced IDs remain for the next retry.
     * Deduplication ensures only one instance is queued at a time.
     */
    data class SyncMissingArtists(val placeholder: String = "") : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = KEY
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = ""

        companion object {
            const val KEY = "SyncMissingArtists"
        }
    }

    /**
     * Peeks up to 50 track IDs from app_sync_pool and syncs them via the Spotify bulk tracks endpoint.
     * Successfully synced IDs are removed from the pool; unsynced IDs remain for the next retry.
     * Deduplication ensures only one instance is queued at a time.
     */
    data class SyncMissingTracks(val placeholder: String = "") : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = KEY
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = ""

        companion object {
            const val KEY = "SyncMissingTracks"
        }
    }


    companion object {
        fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
            FetchCurrentlyPlaying.KEY -> FetchCurrentlyPlaying(UserId(payload))
            FetchRecentlyPlayed.KEY -> FetchRecentlyPlayed(UserId(payload))
            UpdateUserProfile.KEY -> UpdateUserProfile(UserId(payload))
            SyncPlaylistInfo.KEY -> SyncPlaylistInfo(UserId(payload))
            SyncPlaylistData.KEY -> SyncPlaylistData.fromPayload(payload)
            RebuildPlaybackData.KEY -> RebuildPlaybackData(UserId(payload))
            AppendPlaybackData.KEY -> AppendPlaybackData(UserId(payload))
            SyncArtistDetails.KEY, SyncArtistDetails.LEGACY_KEY -> SyncArtistDetails.fromPayload(payload)
            SyncTrackDetails.KEY, SyncTrackDetails.LEGACY_KEY -> SyncTrackDetails.fromPayload(payload)
            SyncMissingArtists.KEY -> SyncMissingArtists()
            SyncMissingTracks.KEY -> SyncMissingTracks()
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
