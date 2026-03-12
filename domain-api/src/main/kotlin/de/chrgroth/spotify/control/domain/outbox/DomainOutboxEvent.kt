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
     * Fetches genres for a single artist from the Spotify API and updates app_artist.
     * Deduplication is by artistId only (artist data is shared across users).
     * payload = "$artistId:${userId.value}"
     */
    data class EnrichArtistDetails(val artistId: String, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$artistId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "$artistId:${userId.value}"

        companion object {
            const val KEY = "EnrichArtistDetails"
            fun fromPayload(payload: String): EnrichArtistDetails {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid EnrichArtistDetails payload: $payload" }
                return EnrichArtistDetails(
                    artistId = payload.substring(0, colonIndex),
                    userId = UserId(payload.substring(colonIndex + 1)),
                )
            }
        }
    }

    /**
     * Fetches track/album details for a single track from the Spotify API,
     * updates app_track.albumId, and upserts the app_album entry.
     * Deduplication is by trackId only (track data is shared across users).
     * payload = "$trackId:${userId.value}"
     */
    data class EnrichTrackDetails(val trackId: String, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$trackId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "$trackId:${userId.value}"

        companion object {
            const val KEY = "EnrichTrackDetails"
            fun fromPayload(payload: String): EnrichTrackDetails {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid EnrichTrackDetails payload: $payload" }
                return EnrichTrackDetails(
                    trackId = payload.substring(0, colonIndex),
                    userId = UserId(payload.substring(colonIndex + 1)),
                )
            }
        }
    }

    /**
     * Bulk-fetches track/album details for up to [BATCH_SIZE] tracks from the Spotify API
     * using GET /v1/tracks?ids=..., updates app_track.albumId for each track, and upserts
     * the corresponding app_album entries. Automatically falls back to individual
     * [EnrichTrackDetails]-style requests when the bulk endpoint becomes unavailable.
     * Deduplication is by the sorted set of trackIds (track data is shared across users).
     * payload = "${userId.value}:trackId1,trackId2,..."
     */
    data class EnrichTrackDetailsBulk(val trackIds: List<String>, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:${trackIds.sorted().joinToString(",")}"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "${userId.value}:${trackIds.joinToString(",")}"

        companion object {
            const val KEY = "EnrichTrackDetailsBulk"
            const val BATCH_SIZE = 50
            fun fromPayload(payload: String): EnrichTrackDetailsBulk {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid EnrichTrackDetailsBulk payload: $payload" }
                return EnrichTrackDetailsBulk(
                    userId = UserId(payload.substring(0, colonIndex)),
                    trackIds = payload.substring(colonIndex + 1).split(","),
                )
            }
        }
    }

    /**
     * Fetches album details for a single album from the Spotify API,
     * and updates app_album with albumTitle, imageLink, genres, and artistId.
     * Deduplication is by albumId only (album data is shared across users).
     * payload = "$albumId:${userId.value}"
     */
    data class EnrichAlbumDetails(val albumId: String, val userId: UserId) : DomainOutboxEvent {
        override val key = KEY
        override fun deduplicationKey() = "$KEY:$albumId"
        override val partition = DomainOutboxPartition.ToSpotify
        override fun toPayload() = "$albumId:${userId.value}"

        companion object {
            const val KEY = "EnrichAlbumDetails"
            fun fromPayload(payload: String): EnrichAlbumDetails {
                val colonIndex = payload.indexOf(':')
                require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid EnrichAlbumDetails payload: $payload" }
                return EnrichAlbumDetails(
                    albumId = payload.substring(0, colonIndex),
                    userId = UserId(payload.substring(colonIndex + 1)),
                )
            }
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
            EnrichArtistDetails.KEY -> EnrichArtistDetails.fromPayload(payload)
            EnrichTrackDetails.KEY -> EnrichTrackDetails.fromPayload(payload)
            EnrichTrackDetailsBulk.KEY -> EnrichTrackDetailsBulk.fromPayload(payload)
            EnrichAlbumDetails.KEY -> EnrichAlbumDetails.fromPayload(payload)
            else -> throw IllegalArgumentException("Unknown outbox event type: $key")
        }
    }
}
