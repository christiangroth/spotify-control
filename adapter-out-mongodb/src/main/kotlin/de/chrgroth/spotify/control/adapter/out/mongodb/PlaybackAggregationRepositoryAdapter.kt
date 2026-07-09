package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityTimeWindow
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.DailyPlaybackSummary
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.DayOfWeek
import kotlinx.datetime.LocalDate

@ApplicationScoped
class PlaybackAggregationRepositoryAdapter(
  private val repository: PlaybackAggregationDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaybackAggregationRepositoryPort {

  override fun save(aggregation: PlaybackAggregation) {
    val doc = aggregation.toDocument()
    mongoQueryMetrics.timed("app_playback_aggregation.save") {
      repository.persistOrUpdate(doc)
    }
  }

  override fun deleteAll() {
    mongoQueryMetrics.timed("app_playback_aggregation.deleteAll") {
      repository.deleteAll()
    }
  }

  override fun findByPeriod(type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation? {
    val id = documentId(type, periodStart)
    return mongoQueryMetrics.timed("app_playback_aggregation.findByPeriod") {
      repository.findById(id)?.toDomain()
    }
  }

  override fun findByPeriods(periods: List<Pair<AggregationPeriodType, LocalDate>>): Map<Pair<AggregationPeriodType, LocalDate>, PlaybackAggregation> {
    if (periods.isEmpty()) return emptyMap()
    val keyByDocumentId = periods.associateBy { documentId(it.first, it.second) }
    return mongoQueryMetrics.timed("app_playback_aggregation.findByPeriods") {
      repository.mongoCollection()
        .find(Filters.`in`("_id", keyByDocumentId.keys.toList()))
        .toList()
        .mapNotNull { doc -> keyByDocumentId[doc.id]?.let { key -> key to doc.toDomain() } }
        .toMap()
    }
  }

  override fun findByTypeAndPeriodRange(type: AggregationPeriodType, from: LocalDate, to: LocalDate): List<PlaybackAggregation> =
    mongoQueryMetrics.timed("app_playback_aggregation.findByTypeAndPeriodRange") {
      repository.mongoCollection()
        .find(
          Filters.and(
            Filters.eq(TYPE_FIELD, type.name),
            Filters.gte(PERIOD_START_FIELD, from.toString()),
            Filters.lte(PERIOD_START_FIELD, to.toString()),
          ),
        )
        .toList()
        .map { it.toDomain() }
    }

  override fun findDailySummaryByPeriodRange(from: LocalDate, to: LocalDate): List<DailyPlaybackSummary> =
    mongoQueryMetrics.timed("app_playback_aggregation.findDailySummaryByPeriodRange") {
      repository.mongoCollection()
        .find(
          Filters.and(
            Filters.eq(TYPE_FIELD, AggregationPeriodType.DAY.name),
            Filters.gte(PERIOD_START_FIELD, from.toString()),
            Filters.lte(PERIOD_START_FIELD, to.toString()),
          ),
        )
        .projection(Projections.include(PERIOD_START_FIELD, EVENT_COUNT_FIELD, TOTAL_PLAYBACK_SECONDS_FIELD, ARTIST_ENTRIES_FIELD, TRACK_ENTRIES_FIELD))
        .toList()
        .map { it.toSummary() }
    }

  override fun sumEventCount(): Long =
    mongoQueryMetrics.timed("app_playback_aggregation.sumEventCount") {
      repository.mongoCollection()
        .find(Filters.eq(TYPE_FIELD, AggregationPeriodType.DAY.name))
        .projection(Projections.include(EVENT_COUNT_FIELD))
        .toList()
        .sumOf { it.eventCount }
    }

  private fun PlaybackAggregation.toDocument(): PlaybackAggregationDocument = PlaybackAggregationDocument().apply {
    id = documentId(this@toDocument.type, this@toDocument.periodStart)
    type = this@toDocument.type.name
    periodStart = this@toDocument.periodStart.toString()
    totalPlaybackSeconds = this@toDocument.totalPlaybackSeconds
    eventCount = this@toDocument.eventCount
    distinctArtistCount = this@toDocument.distinctArtistCount
    distinctTrackCount = this@toDocument.distinctTrackCount
    distinctAlbumCount = this@toDocument.distinctAlbumCount
    artistEntries = this@toDocument.artistEntries.map { it.toEntryDocument() }
    albumEntries = this@toDocument.albumEntries.map { it.toEntryDocument() }
    trackEntries = this@toDocument.trackEntries.map { it.toEntryDocument() }
    activityEntries = this@toDocument.activityEntries.map { it.toActivityDocument() }
  }

  private fun AggregationRankEntry.toEntryDocument(): PlaybackAggregationEntryDocument = PlaybackAggregationEntryDocument().apply {
    id = this@toEntryDocument.id
    name = this@toEntryDocument.name
    totalSeconds = this@toEntryDocument.totalSeconds
  }

  private fun ActivityEntry.toActivityDocument(): PlaybackAggregationActivityEntryDocument = PlaybackAggregationActivityEntryDocument().apply {
    dayOfWeek = this@toActivityDocument.dayOfWeek.name
    timeWindow = this@toActivityDocument.timeWindow.name
    totalSeconds = this@toActivityDocument.totalSeconds
  }

  private fun PlaybackAggregationDocument.toDomain(): PlaybackAggregation = PlaybackAggregation(
    type = AggregationPeriodType.valueOf(type),
    periodStart = LocalDate.parse(periodStart),
    totalPlaybackSeconds = totalPlaybackSeconds,
    eventCount = eventCount,
    distinctArtistCount = distinctArtistCount,
    distinctTrackCount = distinctTrackCount,
    distinctAlbumCount = distinctAlbumCount,
    artistEntries = artistEntries.map { it.toDomain() },
    albumEntries = albumEntries.map { it.toDomain() },
    trackEntries = trackEntries.map { it.toDomain() },
    activityEntries = activityEntries.map { it.toDomain() },
  )

  private fun PlaybackAggregationDocument.toSummary(): DailyPlaybackSummary = DailyPlaybackSummary(
    periodStart = LocalDate.parse(periodStart),
    totalPlaybackSeconds = totalPlaybackSeconds,
    eventCount = eventCount,
    artistEntries = artistEntries.map { it.toDomain() },
    trackEntries = trackEntries.map { it.toDomain() },
  )

  private fun PlaybackAggregationEntryDocument.toDomain(): AggregationRankEntry = AggregationRankEntry(
    id = id,
    name = name,
    totalSeconds = totalSeconds,
  )

  private fun PlaybackAggregationActivityEntryDocument.toDomain(): ActivityEntry = ActivityEntry(
    dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
    timeWindow = ActivityTimeWindow.valueOf(timeWindow),
    totalSeconds = totalSeconds,
  )

  companion object {
    internal const val TYPE_FIELD = "type"
    internal const val PERIOD_START_FIELD = "periodStart"
    internal const val EVENT_COUNT_FIELD = "eventCount"
    internal const val TOTAL_PLAYBACK_SECONDS_FIELD = "totalPlaybackSeconds"
    internal const val ARTIST_ENTRIES_FIELD = "artistEntries"
    internal const val TRACK_ENTRIES_FIELD = "trackEntries"

    internal fun documentId(type: AggregationPeriodType, periodStart: LocalDate): String =
      "${type.name}:$periodStart"
  }
}
