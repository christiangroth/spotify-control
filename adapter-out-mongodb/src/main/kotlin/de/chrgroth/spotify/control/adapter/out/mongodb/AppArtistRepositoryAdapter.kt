package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppArtistRepositoryAdapter : AppArtistRepositoryPort {

  @Inject
  lateinit var appArtistDocumentRepository: AppArtistDocumentRepository

  @Inject
  lateinit var mongoQueryMetrics: MongoQueryMetrics

  override fun upsertAll(items: List<AppArtist>) {
    if (items.isEmpty()) return
    val collection = appArtistDocumentRepository.mongoCollection()
    val upsertOptions = UpdateOptions().upsert(true)
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_artist.upsertAll") {
      val requests = items.map { item ->
        UpdateOneModel<AppArtistDocument>(
          Filters.eq("_id", item.id.value),
          Updates.combine(
            Updates.set("artistName", item.artistName),
            Updates.set("imageLink", item.imageLink),
            Updates.set("type", item.type),
            Updates.set("lastSync", now),
            Updates.setOnInsert("playbackProcessingStatus", item.playbackProcessingStatus.name),
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

  override fun findByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findByPlaybackProcessingStatus") {
      appArtistDocumentRepository.list("playbackProcessingStatus = ?1", status).map { it.toDomain() }
    }

  override fun findByPlaybackProcessingStatusPaged(
    status: ArtistPlaybackProcessingStatus,
    offset: Int,
    limit: Int,
  ): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findByPlaybackProcessingStatusPaged") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.eq("playbackProcessingStatus", status.name))
        .sort(Sorts.ascending("artistName"))
        .skip(offset)
        .limit(limit)
        .toList()
        .map { it.toDomain() }
    }

  override fun countByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): Long =
    mongoQueryMetrics.timed("app_artist.countByPlaybackProcessingStatus") {
      appArtistDocumentRepository.mongoCollection()
        .countDocuments(Filters.eq("playbackProcessingStatus", status.name))
    }

  override fun findByArtistIds(artistIds: Set<ArtistId>): List<AppArtist> {
    if (artistIds.isEmpty()) return emptyList()
    return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.`in`("_id", artistIds.map { it.value }))
        .toList()
        .map { it.toDomain() }
    }
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

  override fun updatePlaybackProcessingStatus(artistId: ArtistId, status: ArtistPlaybackProcessingStatus) {
    mongoQueryMetrics.timed("app_artist.updatePlaybackProcessingStatus") {
      appArtistDocumentRepository.mongoCollection().updateOne(
        Filters.eq("_id", artistId.value),
        Updates.set("playbackProcessingStatus", status.name),
      )
    }
  }

  override fun deleteAll() {
    logger.info { "Deleting all app_artist documents" }
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
    playbackProcessingStatus = playbackProcessingStatus,
  )

  companion object : KLogging()
}
