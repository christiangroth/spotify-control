package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.util.regex.Pattern
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant


@ApplicationScoped
class AppArtistRepositoryAdapter(
  private val appArtistDocumentRepository: AppArtistDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppArtistRepositoryPort {

  override fun upsertAll(items: List<AppArtist>) {
    if (items.isEmpty()) return
    val collection = appArtistDocumentRepository.mongoCollection()
    val upsertOptions = UpdateOptions().upsert(true)
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_artist.upsertAll") {
      val requests = items.map { item ->
        UpdateOneModel<AppArtistDocument>(
          Filters.eq(ID_FIELD, item.id.value),
          Updates.combine(
            Updates.set(ARTIST_NAME_FIELD, item.artistName),
            Updates.set(IMAGE_LINK_FIELD, item.imageLink),
            Updates.set(TYPE_FIELD, item.type),
            Updates.set(LAST_SYNC_FIELD, now),
            Updates.setOnInsert(SYNC_STATUS_FIELD, item.syncStatus.name),
            Updates.setOnInsert(FOLLOWED_FIELD, item.followed),
            Updates.setOnInsert(FOLLOWED_SINCE_FIELD, item.followedSince?.toJavaInstant()),
            Updates.setOnInsert(LAST_FOLLOW_SYNC_FIELD, item.lastFollowSync?.toJavaInstant()),
            Updates.setOnInsert(SINGULARITY_INCLUDED_FIELD, item.singularityIncluded),
            Updates.setOnInsert(SINGULARITY_CURRENT_TRACK_ADDED_AT_FIELD, item.singularityCurrentTrackAddedAt?.toJavaInstant()),
            Updates.setOnInsert(SINGULARITY_REVIEWED_UNTIL_FIELD, item.singularityReviewedUntil?.toJavaInstant()),
            Updates.setOnInsert(SINGULARITY_CURRENT_TRACK_ID_FIELD, item.singularityCurrentTrackId?.value),
          ),
          upsertOptions,
        )
      }
      collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
    }
  }

  override fun findAll(): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findAll") {
      appArtistDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_artist.countAll") {
      appArtistDocumentRepository.count()
    }

  override fun countByStatuses(statuses: Set<ArtistSyncStatus>): Long =
    mongoQueryMetrics.timed("app_artist.countByStatuses") {
      appArtistDocumentRepository.mongoCollection()
        .countDocuments(Filters.`in`(SYNC_STATUS_FIELD, statuses.map { it.name }))
    }

  override fun findByStatuses(statuses: Set<ArtistSyncStatus>, limit: Int): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findByStatuses") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.`in`(SYNC_STATUS_FIELD, statuses.map { it.name }))
        .limit(limit)
        .toList()
        .map { it.toDomain() }
    }

  override fun findByArtistIds(artistIds: Set<ArtistId>): List<AppArtist> {
    if (artistIds.isEmpty()) return emptyList()
    return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.`in`(ID_FIELD, artistIds.map { it.value }))
        .toList()
        .map { it.toDomain() }
    }
  }

  override fun findFollowed(): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findFollowed") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.eq(FOLLOWED_FIELD, true))
        .toList()
        .map { it.toDomain() }
    }

  override fun searchByName(filter: String, limit: Int): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.searchByName") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.regex(ARTIST_NAME_FIELD, Pattern.quote(filter), "i"))
        .limit(limit)
        .toList()
        .map { it.toDomain() }
    }

  override fun findWithImageLinkAndBlankName(): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findWithImageLinkAndBlankName") {
      appArtistDocumentRepository.mongoCollection()
        .find(
          Filters.and(
            Filters.ne("imageLink", null),
            Filters.or(
              Filters.eq("artistName", ""),
              Filters.exists("artistName", false),
            ),
          ),
        )
        .toList()
        .map { it.toDomain() }
    }

  override fun findRecentlySynced(offset: Int, limit: Int): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findRecentlySynced") {
      appArtistDocumentRepository.mongoCollection()
        .find()
        .sort(Sorts.descending(LAST_SYNC_FIELD))
        .skip(offset)
        .limit(limit)
        .toList()
        .map { it.toDomain() }
    }

  override fun setSyncStatus(artistId: ArtistId, status: ArtistSyncStatus) {
    mongoQueryMetrics.timed("app_artist.setSyncStatus") {
      appArtistDocumentRepository.mongoCollection()
        .updateOne(
          Filters.eq(ID_FIELD, artistId.value),
          Updates.set(SYNC_STATUS_FIELD, status.name),
        )
    }
  }

  override fun setFollowed(artistId: ArtistId, followed: Boolean, followedSince: Instant?) {
    mongoQueryMetrics.timed("app_artist.setFollowed") {
      appArtistDocumentRepository.mongoCollection()
        .updateOne(
          Filters.eq(ID_FIELD, artistId.value),
          Updates.combine(
            Updates.set(FOLLOWED_FIELD, followed),
            Updates.set(FOLLOWED_SINCE_FIELD, followedSince?.toJavaInstant()),
          ),
        )
    }
  }

  override fun touchFollowedSync(artistIds: Set<ArtistId>, syncedAt: Instant) {
    if (artistIds.isEmpty()) return
    mongoQueryMetrics.timed("app_artist.touchFollowedSync") {
      appArtistDocumentRepository.mongoCollection()
        .updateMany(
          Filters.`in`(ID_FIELD, artistIds.map { it.value }),
          Updates.set(LAST_FOLLOW_SYNC_FIELD, syncedAt.toJavaInstant()),
        )
    }
  }

  override fun initializeSingularityIncluded(includedArtistIds: Set<ArtistId>) {
    mongoQueryMetrics.timed("app_artist.initializeSingularityIncluded") {
      val collection = appArtistDocumentRepository.mongoCollection()
      val includedIds = includedArtistIds.map { it.value }
      collection.updateMany(Filters.`in`(ID_FIELD, includedIds), Updates.set(SINGULARITY_INCLUDED_FIELD, true))
      collection.updateMany(Filters.nin(ID_FIELD, includedIds), Updates.set(SINGULARITY_INCLUDED_FIELD, false))
    }
  }

  override fun initializeSingularityTracking(artistId: ArtistId, addedAt: Instant) {
    mongoQueryMetrics.timed("app_artist.initializeSingularityTracking") {
      appArtistDocumentRepository.mongoCollection()
        .updateOne(
          Filters.eq(ID_FIELD, artistId.value),
          Updates.combine(
            Updates.set(SINGULARITY_CURRENT_TRACK_ADDED_AT_FIELD, addedAt.toJavaInstant()),
            Updates.set(SINGULARITY_REVIEWED_UNTIL_FIELD, addedAt.toJavaInstant()),
          ),
        )
    }
  }

  override fun updateSingularityCurrentTrack(artistId: ArtistId, trackId: TrackId, addedAt: Instant) {
    mongoQueryMetrics.timed("app_artist.updateSingularityCurrentTrack") {
      appArtistDocumentRepository.mongoCollection()
        .updateOne(
          Filters.eq(ID_FIELD, artistId.value),
          Updates.combine(
            Updates.set(SINGULARITY_CURRENT_TRACK_ADDED_AT_FIELD, addedAt.toJavaInstant()),
            Updates.set(SINGULARITY_CURRENT_TRACK_ID_FIELD, trackId.value),
          ),
        )
    }
  }

  override fun advanceSingularityReviewedUntil(artistId: ArtistId, reviewedUntil: Instant) {
    mongoQueryMetrics.timed("app_artist.advanceSingularityReviewedUntil") {
      appArtistDocumentRepository.mongoCollection()
        .updateOne(
          Filters.eq(ID_FIELD, artistId.value),
          Updates.max(SINGULARITY_REVIEWED_UNTIL_FIELD, reviewedUntil.toJavaInstant()),
        )
    }
  }

  override fun deleteAll() {
    mongoQueryMetrics.timed("app_artist.deleteAll") {
      appArtistDocumentRepository.deleteAll()
    }
  }

  private fun AppArtistDocument.toDomain() = AppArtist(
    id = ArtistId(id),
    artistName = artistName,
    imageLink = imageLink,
    type = type,
    lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
    syncStatus = ArtistSyncStatus.valueOf(syncStatus),
    followed = followed,
    followedSince = followedSince?.toKotlinInstant(),
    lastFollowSync = lastFollowSync?.toKotlinInstant(),
    singularityIncluded = singularityIncluded,
    singularityCurrentTrackAddedAt = singularityCurrentTrackAddedAt?.toKotlinInstant(),
    singularityReviewedUntil = singularityReviewedUntil?.toKotlinInstant(),
    singularityCurrentTrackId = singularityCurrentTrackId?.let { TrackId(it) },
  )

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val ARTIST_NAME_FIELD = "artistName"
    internal const val IMAGE_LINK_FIELD = "imageLink"
    internal const val TYPE_FIELD = "type"
    internal const val LAST_SYNC_FIELD = "lastSync"
    internal const val SYNC_STATUS_FIELD = "syncStatus"
    internal const val FOLLOWED_FIELD = "followed"
    internal const val FOLLOWED_SINCE_FIELD = "followedSince"
    internal const val LAST_FOLLOW_SYNC_FIELD = "lastFollowSync"
    internal const val SINGULARITY_INCLUDED_FIELD = "singularityIncluded"
    internal const val SINGULARITY_CURRENT_TRACK_ADDED_AT_FIELD = "singularityCurrentTrackAddedAt"
    internal const val SINGULARITY_REVIEWED_UNTIL_FIELD = "singularityReviewedUntil"
    internal const val SINGULARITY_CURRENT_TRACK_ID_FIELD = "singularityCurrentTrackId"
  }
}
