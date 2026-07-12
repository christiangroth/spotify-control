package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import org.bson.Document

@ApplicationScoped
class AppPlaybackRepositoryAdapter(
  private val appPlaybackDocumentRepository: AppPlaybackDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppPlaybackRepositoryPort {

  override fun saveAll(items: List<AppPlaybackItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      AppPlaybackDocument().apply {
        id = item.playedAt.toEpochMilliseconds().toString()
        playedAt = item.playedAt.toJavaInstant()
        trackId = item.trackId
        secondsPlayed = item.secondsPlayed
      }
    }
    mongoQueryMetrics.timed("app_playback.saveAll") {
      appPlaybackDocumentRepository.persistOrUpdate(documents)
    }
  }

  override fun deleteAll() {
    mongoQueryMetrics.timed("app_playback.deleteAll") {
      appPlaybackDocumentRepository.deleteAll()
    }
  }

  override fun deleteAllByTrackIds(trackIds: Set<String>) {
    if (trackIds.isEmpty()) return
    mongoQueryMetrics.timed("app_playback.deleteAllByTrackIds") {
      appPlaybackDocumentRepository.delete("trackId in ?1", trackIds.toList())
    }
  }

  override fun deleteByPlayedAts(playedAts: Set<Instant>) {
    if (playedAts.isEmpty()) return
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    mongoQueryMetrics.timed("app_playback.deleteByPlayedAts") {
      appPlaybackDocumentRepository.delete("playedAt in ?1", javaPlayedAts)
    }
  }

  override fun findMostRecentPlayedAt(): Instant? =
    mongoQueryMetrics.timed("app_playback.findMostRecentPlayedAt") {
      appPlaybackDocumentRepository
        .findAll(Sort.by("playedAt").descending())
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }

  override fun findOldestPlayedAt(): Instant? =
    mongoQueryMetrics.timed("app_playback.findOldestPlayedAt") {
      appPlaybackDocumentRepository
        .findAll(Sort.by("playedAt").ascending())
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }

  override fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("app_playback.findExistingPlayedAts") {
      appPlaybackDocumentRepository
        .list("playedAt in ?1", javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_playback.countAll") {
      appPlaybackDocumentRepository.count()
    }

  override fun countSince(since: Instant): Long =
    mongoQueryMetrics.timed("app_playback.countSince") {
      appPlaybackDocumentRepository.count("playedAt >= ?1", since.toJavaInstant())
    }

  override fun countPerDaySince(since: Instant): List<Pair<LocalDate, Long>> {
    val pipeline = listOf(
      Aggregates.match(Filters.gte(PLAYED_AT_FIELD, since.toJavaInstant())),
      Aggregates.group(
        Document("\$dateToString", Document("format", "%Y-%m-%d").append("date", "\$$PLAYED_AT_FIELD")),
        Accumulators.sum("count", 1),
      ),
      Aggregates.sort(Sorts.ascending("_id")),
    )
    return mongoQueryMetrics.timed("app_playback.countPerDaySince") {
      appPlaybackDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .map { doc ->
          val dateStr = doc.getString("_id")
          val count = (doc["count"] as? Int)?.toLong() ?: doc.getLong("count") ?: 0L
          LocalDate.parse(dateStr) to count
        }
        .toList()
    }
  }

  override fun findRecentlyPlayed(limit: Int): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findRecentlyPlayed") {
      appPlaybackDocumentRepository
        .findAll(Sort.by("playedAt").descending())
        .page(0, limit)
        .list()
        .map { doc -> doc.toItem() }
    }

  override fun findAllSince(since: Instant): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findAllSince") {
      appPlaybackDocumentRepository
        .list("playedAt >= ?1", since.toJavaInstant())
        .map { doc -> doc.toItem() }
    }

  override fun findAllBetween(from: Instant, to: Instant): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findAllBetween") {
      appPlaybackDocumentRepository
        .mongoCollection()
        .find(
          Filters.and(
            Filters.gte(PLAYED_AT_FIELD, from.toJavaInstant()),
            Filters.lt(PLAYED_AT_FIELD, to.toJavaInstant()),
          ),
        )
        .toList()
        .map { doc -> doc.toItem() }
    }

  override fun sumSecondsPlayedByTrackIdSince(since: Instant): Map<String, Long> {
    val pipeline = listOf(
      Aggregates.match(
        Filters.and(
          Filters.gte(PLAYED_AT_FIELD, since.toJavaInstant()),
          Filters.gt(SECONDS_PLAYED_FIELD, 0),
        ),
      ),
      Aggregates.group("\$$TRACK_ID_FIELD", Accumulators.sum("totalSeconds", "\$$SECONDS_PLAYED_FIELD")),
    )
    return mongoQueryMetrics.timed("app_playback.sumSecondsPlayedByTrackIdSince") {
      appPlaybackDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .associate { doc ->
          val trackId = doc.getString("_id")
          val totalSeconds = (doc["totalSeconds"] as? Int)?.toLong() ?: doc.getLong("totalSeconds") ?: 0L
          trackId to totalSeconds
        }
    }
  }

  override fun findAllDistinctTrackIds(): Set<String> =
    mongoQueryMetrics.timed("app_playback.findAllDistinctTrackIds") {
      appPlaybackDocumentRepository.mongoCollection()
        .distinct(TRACK_ID_FIELD, String::class.java)
        .toList()
        .toSet()
    }

  override fun findDistinctPlayedDatesByTrackIds(trackIds: Set<String>): Set<LocalDate> {
    if (trackIds.isEmpty()) return emptySet()
    val pipeline = listOf(
      Aggregates.match(Filters.`in`(TRACK_ID_FIELD, trackIds)),
      Aggregates.group(Document("\$dateToString", Document("format", "%Y-%m-%d").append("date", "\$$PLAYED_AT_FIELD"))),
    )
    return mongoQueryMetrics.timed("app_playback.findDistinctPlayedDatesByTrackIds") {
      appPlaybackDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .map { doc -> LocalDate.parse(doc.getString("_id")) }
        .toSet()
    }
  }

  private fun AppPlaybackDocument.toItem() = AppPlaybackItem(
    playedAt = playedAt.toKotlinInstant(),
    trackId = trackId,
    secondsPlayed = secondsPlayed,
  )

  companion object {
    internal const val PLAYED_AT_FIELD = "playedAt"
    internal const val TRACK_ID_FIELD = "trackId"
    internal const val SECONDS_PLAYED_FIELD = "secondsPlayed"
  }
}
