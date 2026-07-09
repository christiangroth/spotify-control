package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.OutboxEventPriority
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import kotlinx.datetime.LocalDate

sealed interface DomainOutboxEvent : ApplicationOutboxEvent {
  override val partition: DomainOutboxPartition
  override val priority: OutboxEventPriority get() = OutboxEventPriority.MEDIUM
  override val serializePayload: String

  data class FetchPlaybackData(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.ToSpotifyPlayback
    override val priority = OutboxEventPriority.HIGH
    override val serializePayload = ""

    companion object {
      const val KEY = "FetchPlaybackData"
    }
  }

  data class UpdateUserProfile(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.ToSpotifyUser
    override val serializePayload = ""

    companion object {
      const val KEY = "UpdateUserProfile"
    }
  }

  data class SyncPlaylistInfo(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.ToSpotifyPlaylist
    override val serializePayload = ""

    companion object {
      const val KEY = "SyncPlaylistInfo"
    }
  }

  /**
   * Syncs track data for a specific page of a playlist.
   * [nextUrl] is the Spotify API URL for the page to fetch; `null` means fetch the first page.
   * [snapshotId] is the Spotify snapshot ID observed when the previous page was fetched; `null` for the first page.
   * If the fetched page's snapshot ID differs from [snapshotId], the sync restarts from the first page.
   * The deduplication key includes both [snapshotId] and [nextUrl] so that each page+snapshot combination
   * can be queued independently while retries of the same page are still correctly deduplicated.
   * payload: "$playlistId" for the first page;
   *          "$playlistId\n$snapshotId\n$nextUrl" for subsequent pages.
   * Legacy payload (no snapshotId): "$playlistId\n$nextUrl" — parsed with snapshotId=null.
   */
  data class SyncPlaylistData(val playlistId: String, val nextUrl: String? = null, val snapshotId: String? = null) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$playlistId:${snapshotId ?: ""}:${nextUrl ?: ""}"
    override val partition = DomainOutboxPartition.ToSpotifyPlaylist
    override val serializePayload = when {
      nextUrl == null -> playlistId
      snapshotId != null -> "$playlistId\n$snapshotId\n$nextUrl"
      else -> "$playlistId\n$nextUrl"
    }

    companion object {
      const val KEY = "SyncPlaylistData"
      fun fromPayload(payload: String): SyncPlaylistData {
        val firstNewline = payload.indexOf('\n')
        if (firstNewline < 0) {
          return SyncPlaylistData(playlistId = payload.substringAfter(':'))
        }
        val playlistId = payload.substring(0, firstNewline).substringAfter(':')
        val afterFirst = payload.substring(firstNewline + 1)
        val secondNewline = afterFirst.indexOf('\n')
        return if (secondNewline < 0) {
          // Legacy format: no snapshotId
          SyncPlaylistData(playlistId, afterFirst)
        } else {
          // New format: snapshotId\nnextUrl
          val snapshotId = afterFirst.substring(0, secondNewline).takeIf { it.isNotEmpty() }
          val nextUrl = afterFirst.substring(secondNewline + 1)
          SyncPlaylistData(playlistId, nextUrl, snapshotId)
        }
      }
    }
  }

  data class RebuildPlaybackData(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "RebuildPlaybackData"
    }
  }

  data class AppendPlaybackData(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "AppendPlaybackData"
    }
  }

  /**
   * Syncs genres and images for a single artist from the Spotify API and updates app_artist.
   * Deduplication is by artistId only (artist data is shared across users).
   * payload = artistId
   */
  data class SyncArtistDetails(val artistId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.ToSpotifyCatalog
    override val serializePayload = artistId

    companion object {
      const val KEY = "SyncArtistDetails"
      const val LEGACY_KEY = "EnrichArtistDetails"
      fun fromPayload(payload: String): SyncArtistDetails = SyncArtistDetails(artistId = payload.substringBefore(':'))
    }
  }

  /**
   * Syncs a single album by fetching all its tracks via GET /v1/albums/{id}.
   * All returned tracks are upserted. Does not enqueue further artist syncs, to avoid
   * an unbounded fanout into artists without playback events.
   * payload = albumId
   */
  data class SyncAlbumDetails(val albumId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$albumId"
    override val partition = DomainOutboxPartition.ToSpotifyCatalog
    override val serializePayload = albumId

    companion object {
      const val KEY = "SyncAlbumDetails"
      fun fromPayload(payload: String): SyncAlbumDetails = SyncAlbumDetails(payload)
    }
  }

  /**
   * Syncs all album IDs for a single artist from the Spotify API and enqueues
   * SyncAlbumDetails for any albums not yet in the catalog.
   * Each page is fetched in a separate outbox task to avoid rate limit bursts.
   * Deduplication is by artistId and nextUrl so that each page can be queued independently.
   * payload: "$artistId" for the first page;
   *          "$artistId\n$nextUrl" for subsequent pages.
   */
  data class SyncArtistAlbums(val artistId: String, val nextUrl: String? = null) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId:${nextUrl ?: ""}"
    override val partition = DomainOutboxPartition.ToSpotifyCatalog
    override val serializePayload = when {
      nextUrl == null -> artistId
      else -> "$artistId\n$nextUrl"
    }

    companion object {
      const val KEY = "SyncArtistAlbums"
      fun fromPayload(payload: String): SyncArtistAlbums {
        val newlineIndex = payload.indexOf('\n')
        return if (newlineIndex < 0) {
          SyncArtistAlbums(artistId = payload.substringBefore(':'))
        } else {
          SyncArtistAlbums(
            artistId = payload.substring(0, newlineIndex).substringBefore(':'),
            nextUrl = payload.substring(newlineIndex + 1),
          )
        }
      }
    }
  }

  /**
   * Re-enqueues sync events for all known artists, tracks, and albums in the catalog
   * so that they are refreshed from Spotify.
   * Deduplication ensures only one instance is queued at a time.
   */
  data class ResyncCatalog(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "ResyncCatalog"
    }
  }

  /**
   * Runs all playlist checks for the user's playlist.
   * payload = playlistId
   */
  data class RunPlaylistChecks(val playlistId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$playlistId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = playlistId

    companion object {
      const val KEY = "RunPlaylistChecks"
      fun fromPayload(payload: String): RunPlaylistChecks = RunPlaylistChecks(payload.substringAfter(':'))
    }
  }

  /**
   * Triggers aggregation of playback data for a specific period.
   * payload = "${type.name}:${periodStart}"
   */
  data class AggregatePlaybackData(val type: AggregationPeriodType, val periodStart: LocalDate) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${type.name}:$periodStart"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "${type.name}:$periodStart"

    companion object {
      const val KEY = "AggregatePlaybackData"
      fun fromPayload(payload: String): AggregatePlaybackData {
        val firstColon = payload.indexOf(':')
        require(firstColon > 0) { "Invalid AggregatePlaybackData payload: $payload" }
        val secondColon = payload.indexOf(':', firstColon + 1)
        return if (secondColon < 0) {
          AggregatePlaybackData(
            type = AggregationPeriodType.valueOf(payload.substring(0, firstColon)),
            periodStart = LocalDate.parse(payload.substring(firstColon + 1)),
          )
        } else {
          // Legacy format: "${userId}:${type}:${periodStart}"
          AggregatePlaybackData(
            type = AggregationPeriodType.valueOf(payload.substring(firstColon + 1, secondColon)),
            periodStart = LocalDate.parse(payload.substring(secondColon + 1)),
          )
        }
      }
    }
  }

  companion object {
    val allKeys: List<String> = listOf(
      FetchPlaybackData.KEY,
      UpdateUserProfile.KEY,
      SyncPlaylistInfo.KEY,
      SyncPlaylistData.KEY,
      RebuildPlaybackData.KEY,
      AppendPlaybackData.KEY,
      SyncArtistDetails.KEY,
      SyncArtistAlbums.KEY,
      SyncAlbumDetails.KEY,
      ResyncCatalog.KEY,
      RunPlaylistChecks.KEY,
      AggregatePlaybackData.KEY,
    )

    @Suppress("CyclomaticComplexMethod")
    fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
      FetchPlaybackData.KEY -> FetchPlaybackData()
      UpdateUserProfile.KEY -> UpdateUserProfile()
      SyncPlaylistInfo.KEY -> SyncPlaylistInfo()
      SyncPlaylistData.KEY -> SyncPlaylistData.fromPayload(payload)
      RebuildPlaybackData.KEY -> RebuildPlaybackData()
      AppendPlaybackData.KEY -> AppendPlaybackData()
      SyncArtistDetails.KEY, SyncArtistDetails.LEGACY_KEY -> SyncArtistDetails.fromPayload(payload)
      SyncArtistAlbums.KEY -> SyncArtistAlbums.fromPayload(payload)
      SyncAlbumDetails.KEY -> SyncAlbumDetails.fromPayload(payload)
      ResyncCatalog.KEY -> ResyncCatalog()
      RunPlaylistChecks.KEY -> RunPlaylistChecks.fromPayload(payload)
      AggregatePlaybackData.KEY -> AggregatePlaybackData.fromPayload(payload)
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
