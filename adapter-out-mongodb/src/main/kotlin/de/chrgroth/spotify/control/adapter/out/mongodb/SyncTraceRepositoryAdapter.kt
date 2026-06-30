package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class SyncTraceRepositoryAdapter(
  private val syncTraceDocumentRepository: SyncTraceDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : SyncTraceRepositoryPort {

  override fun upsert(trace: SyncTrace) {
    mongoQueryMetrics.timed("sync_trace.upsert") {
      syncTraceDocumentRepository.mongoCollection().updateOne(
        Filters.eq(ID_FIELD, documentId(trace.entityType, trace.entityId)),
        Updates.combine(
          Updates.set(ENTITY_TYPE_FIELD, trace.entityType.name),
          Updates.set(ENTITY_ID_FIELD, trace.entityId),
          Updates.set(CAUSE_TYPE_FIELD, causeType(trace.cause)),
          Updates.set(CAUSE_ARTIST_ID_FIELD, causeArtistId(trace.cause)),
          Updates.set(CAUSE_PLAYLIST_ID_FIELD, causePlaylistId(trace.cause)),
          Updates.set(CAUSE_TRACK_ID_FIELD, causeTrackId(trace.cause)),
          Updates.set(TRIGGERED_AT_FIELD, trace.triggeredAt.toJavaInstant()),
        ),
        UpdateOptions().upsert(true),
      )
    }
  }

  override fun find(entityType: SyncTraceEntityType, entityId: String): SyncTrace? =
    mongoQueryMetrics.timed("sync_trace.find") {
      syncTraceDocumentRepository.findById(documentId(entityType, entityId))?.toDomain()
    }

  private fun documentId(entityType: SyncTraceEntityType, entityId: String) = "${entityType.name}:$entityId"

  private fun causeType(cause: SyncCause) = when (cause) {
    is SyncCause.Playback -> CAUSE_TYPE_PLAYBACK
    is SyncCause.Playlist -> CAUSE_TYPE_PLAYLIST
    is SyncCause.ArtistDiscography -> CAUSE_TYPE_ARTIST_DISCOGRAPHY
    is SyncCause.ManualResync -> CAUSE_TYPE_MANUAL_RESYNC
  }

  private fun causeArtistId(cause: SyncCause) = (cause as? SyncCause.ArtistDiscography)?.artistId

  private fun causePlaylistId(cause: SyncCause) = (cause as? SyncCause.Playlist)?.playlistId

  private fun causeTrackId(cause: SyncCause) = when (cause) {
    is SyncCause.Playback -> cause.trackId
    is SyncCause.Playlist -> cause.trackId
    else -> null
  }

  private fun SyncTraceDocument.toDomain(): SyncTrace {
    val cause = when (causeType) {
      CAUSE_TYPE_PLAYBACK -> SyncCause.Playback(requireNotNull(causeTrackId))
      CAUSE_TYPE_PLAYLIST -> SyncCause.Playlist(requireNotNull(causePlaylistId), requireNotNull(causeTrackId))
      CAUSE_TYPE_ARTIST_DISCOGRAPHY -> SyncCause.ArtistDiscography(requireNotNull(causeArtistId))
      else -> SyncCause.ManualResync
    }
    return SyncTrace(
      entityType = SyncTraceEntityType.valueOf(entityType),
      entityId = entityId,
      cause = cause,
      triggeredAt = triggeredAt.toKotlinInstant(),
    )
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val ENTITY_TYPE_FIELD = "entityType"
    internal const val ENTITY_ID_FIELD = "entityId"
    internal const val CAUSE_TYPE_FIELD = "causeType"
    internal const val CAUSE_ARTIST_ID_FIELD = "causeArtistId"
    internal const val CAUSE_PLAYLIST_ID_FIELD = "causePlaylistId"
    internal const val CAUSE_TRACK_ID_FIELD = "causeTrackId"
    internal const val TRIGGERED_AT_FIELD = "triggeredAt"

    private const val CAUSE_TYPE_PLAYBACK = "PLAYBACK"
    private const val CAUSE_TYPE_PLAYLIST = "PLAYLIST"
    private const val CAUSE_TYPE_ARTIST_DISCOGRAPHY = "ARTIST_DISCOGRAPHY"
    private const val CAUSE_TYPE_MANUAL_RESYNC = "MANUAL_RESYNC"
  }
}
