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
   * [fromPlaylist] records whether this artist was discovered via a synced playlist rather than playback history,
   * which determines whether a newly discovered artist starts out as SYNC_ASSUMPTION or SHALLOW_ASSUMPTION.
   * payload: "$artistId" for legacy events (fromPlaylist=false); "$artistId\n$fromPlaylist" otherwise.
   */
  data class SyncArtistDetails(val artistId: String, val fromPlaylist: Boolean = false) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.ToSpotifyCatalog
    override val serializePayload = "$artistId\n$fromPlaylist"

    companion object {
      const val KEY = "SyncArtistDetails"
      const val LEGACY_KEY = "EnrichArtistDetails"
      fun fromPayload(payload: String): SyncArtistDetails {
        val newlineIndex = payload.indexOf('\n')
        return if (newlineIndex < 0) {
          SyncArtistDetails(artistId = payload.substringBefore(':'))
        } else {
          SyncArtistDetails(
            artistId = payload.substring(0, newlineIndex).substringBefore(':'),
            fromPlaylist = payload.substring(newlineIndex + 1).toBoolean(),
          )
        }
      }
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
   * Confirms an artist's guessed sync status as SYNC, re-enqueues its album sync and
   * rebuilds playback aggregations.
   * payload = artistId
   */
  data class ConfirmArtistSync(val artistId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = artistId

    companion object {
      const val KEY = "ConfirmArtistSync"
      fun fromPayload(payload: String): ConfirmArtistSync = ConfirmArtistSync(payload)
    }
  }

  /**
   * Confirms an artist's guessed sync status as SHALLOW, removes its cached albums and
   * tracks, and rebuilds playback aggregations.
   * payload = artistId
   */
  data class ConfirmArtistShallow(val artistId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = artistId

    companion object {
      const val KEY = "ConfirmArtistShallow"
      fun fromPayload(payload: String): ConfirmArtistShallow = ConfirmArtistShallow(payload)
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
   * Deletes all catalog data (artists, albums, tracks), deactivates all playlist syncs
   * and deletes all playlist checks.
   */
  data class WipeCatalog(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "WipeCatalog"
    }
  }

  /**
   * Runs playlist checks for the user's playlist.
   * [checkType] restricts the run to a single check; `null` runs all applicable checks.
   * payload = "$playlistId" when [checkType] is null; "$playlistId\n$checkType" otherwise.
   */
  data class RunPlaylistChecks(val playlistId: String, val checkType: String? = null) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$playlistId:${checkType ?: ""}"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = if (checkType == null) playlistId else "$playlistId\n$checkType"

    companion object {
      const val KEY = "RunPlaylistChecks"
      fun fromPayload(payload: String): RunPlaylistChecks {
        val newlineIndex = payload.indexOf('\n')
        return if (newlineIndex < 0) {
          RunPlaylistChecks(playlistId = payload.substringAfter(':'))
        } else {
          RunPlaylistChecks(playlistId = payload.substring(0, newlineIndex), checkType = payload.substring(newlineIndex + 1))
        }
      }
    }
  }

  /**
   * Applies the fix action of a playlist check to a playlist via the Spotify write API,
   * then re-enqueues [SyncPlaylistData] to refresh the local mirror.
   * payload = "$playlistId\n$checkType"
   */
  data class FixPlaylistCheck(val playlistId: String, val checkType: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$playlistId:$checkType"
    override val partition = DomainOutboxPartition.ToSpotifyPlaylist
    override val serializePayload = "$playlistId\n$checkType"

    companion object {
      const val KEY = "FixPlaylistCheck"
      fun fromPayload(payload: String): FixPlaylistCheck {
        val newlineIndex = payload.indexOf('\n')
        return FixPlaylistCheck(playlistId = payload.substring(0, newlineIndex), checkType = payload.substring(newlineIndex + 1))
      }
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

  /**
   * Deletes all playback aggregation documents and re-enqueues [AggregatePlaybackData]
   * for every period (day/week/month/quarter/year) covered by the existing playback history.
   */
  data class RebuildAllAggregations(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "RebuildAllAggregations"
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
      ConfirmArtistSync.KEY,
      ConfirmArtistShallow.KEY,
      ResyncCatalog.KEY,
      WipeCatalog.KEY,
      RunPlaylistChecks.KEY,
      FixPlaylistCheck.KEY,
      AggregatePlaybackData.KEY,
      RebuildAllAggregations.KEY,
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
      ConfirmArtistSync.KEY -> ConfirmArtistSync.fromPayload(payload)
      ConfirmArtistShallow.KEY -> ConfirmArtistShallow.fromPayload(payload)
      ResyncCatalog.KEY -> ResyncCatalog()
      WipeCatalog.KEY -> WipeCatalog()
      RunPlaylistChecks.KEY -> RunPlaylistChecks.fromPayload(payload)
      FixPlaylistCheck.KEY -> FixPlaylistCheck.fromPayload(payload)
      AggregatePlaybackData.KEY -> AggregatePlaybackData.fromPayload(payload)
      RebuildAllAggregations.KEY -> RebuildAllAggregations()
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
