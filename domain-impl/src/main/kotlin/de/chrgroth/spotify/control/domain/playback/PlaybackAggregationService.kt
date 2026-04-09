package de.chrgroth.spotify.control.domain.playback

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityTimeWindow
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackAggregationService(
  private val userRepository: UserRepositoryPort,
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val outboxPort: OutboxPort,
) : PlaybackAggregationPort {

  // --- Enqueue helpers ---

  override fun enqueueAggregateDay(date: LocalDate) {
    val users = userRepository.findAll()
    logger.info { "Enqueuing day aggregation for $date for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(user.spotifyUserId, AggregationPeriodType.DAY, date))
    }
  }

  override fun enqueueAggregateWeek(weekStart: LocalDate) {
    val users = userRepository.findAll()
    logger.info { "Enqueuing week aggregation for $weekStart for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(user.spotifyUserId, AggregationPeriodType.WEEK, weekStart))
    }
  }

  override fun enqueueAggregateMonth(monthStart: LocalDate) {
    val users = userRepository.findAll()
    logger.info { "Enqueuing month aggregation for $monthStart for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(user.spotifyUserId, AggregationPeriodType.MONTH, monthStart))
    }
  }

  override fun enqueueAggregateQuarter(quarterStart: LocalDate) {
    val users = userRepository.findAll()
    logger.info { "Enqueuing quarter aggregation for $quarterStart for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(user.spotifyUserId, AggregationPeriodType.QUARTER, quarterStart))
    }
  }

  override fun enqueueAggregateYear(yearStart: LocalDate) {
    val users = userRepository.findAll()
    logger.info { "Enqueuing year aggregation for $yearStart for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(user.spotifyUserId, AggregationPeriodType.YEAR, yearStart))
    }
  }

  // --- Outbox handler ---

  override fun handle(event: DomainOutboxEvent.AggregatePlaybackData): Either<DomainError, Unit> {
    when (event.type) {
      AggregationPeriodType.DAY -> aggregateDay(event.userId, event.periodStart)
      AggregationPeriodType.WEEK -> aggregateFromDailyAggregations(event.userId, AggregationPeriodType.WEEK, event.periodStart, event.periodStart.plusKDays(6))
      AggregationPeriodType.MONTH -> aggregateFromDailyAggregations(event.userId, AggregationPeriodType.MONTH, event.periodStart, event.periodStart.endOfMonth())
      AggregationPeriodType.QUARTER -> aggregateFromDailyAggregations(event.userId, AggregationPeriodType.QUARTER, event.periodStart, event.periodStart.plusKMonths(3).minusKDays(1))
      AggregationPeriodType.YEAR -> aggregateFromDailyAggregations(event.userId, AggregationPeriodType.YEAR, event.periodStart, event.periodStart.plusKMonths(12).minusKDays(1))
    }
    return Unit.right()
  }

  // --- Aggregation logic ---

  private fun aggregateDay(userId: UserId, date: LocalDate) {
    logger.info { "Aggregating day $date for user: ${userId.value}" }

    val javaDate = date.toJavaLocalDate()
    val from = Instant.fromEpochMilliseconds(javaDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val to = Instant.fromEpochMilliseconds(javaDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

    val items = appPlaybackRepository.findAllBetween(userId, from, to)
    if (items.isEmpty()) {
      logger.info { "No playback data for $date, user: ${userId.value} — saving empty aggregation" }
      aggregationRepository.save(emptyAggregation(userId, AggregationPeriodType.DAY, date))
      return
    }

    val trackIds = items.map { TrackId(it.trackId) }.toSet()
    val tracks = appTrackRepository.findByTrackIds(trackIds).associateBy { it.id }

    val durationPerTrackId = items.groupBy { it.trackId }.mapValues { (_, entries) -> entries.sumOf { it.secondsPlayed } }

    val durationPerArtistId = mutableMapOf<String, Long>()
    items.forEach { item ->
      val artistId = tracks[TrackId(item.trackId)]?.artistId?.value ?: UNKNOWN_ARTIST_ID
      durationPerArtistId[artistId] = (durationPerArtistId[artistId] ?: 0L) + item.secondsPlayed
    }

    val artistIds = durationPerArtistId.keys.filter { it != UNKNOWN_ARTIST_ID }.map { ArtistId(it) }.toSet()
    val artists = appArtistRepository.findByArtistIds(artistIds).associateBy { it.id }

    val trackEntries = durationPerTrackId.map { (trackId, seconds) ->
      val name = tracks[TrackId(trackId)]?.title ?: trackId
      AggregationRankEntry(id = trackId, name = name, totalSeconds = seconds)
    }.sortedByDescending { it.totalSeconds }

    val artistEntries = durationPerArtistId.map { (artistId, seconds) ->
      val name = artists[ArtistId(artistId)]?.artistName ?: artistId
      AggregationRankEntry(id = artistId, name = name, totalSeconds = seconds)
    }.sortedByDescending { it.totalSeconds }

    val activityEntries = items.groupBy { item ->
      val zdt = item.playedAt.toJavaInstant().atZone(ZoneOffset.UTC)
      zdt.dayOfWeek to ActivityTimeWindow.fromHour(zdt.hour)
    }.map { (key, entries) ->
      ActivityEntry(dayOfWeek = key.first, timeWindow = key.second, totalSeconds = entries.sumOf { it.secondsPlayed })
    }

    val aggregation = PlaybackAggregation(
      userId = userId,
      type = AggregationPeriodType.DAY,
      periodStart = date,
      totalPlaybackSeconds = items.sumOf { it.secondsPlayed },
      distinctArtistCount = artistEntries.size,
      distinctTrackCount = trackEntries.size,
      artistEntries = artistEntries,
      trackEntries = trackEntries,
      activityEntries = activityEntries,
    )
    aggregationRepository.save(aggregation)
    logger.info { "Saved day aggregation for $date, user: ${userId.value}" }
  }

  private fun aggregateFromDailyAggregations(userId: UserId, type: AggregationPeriodType, from: LocalDate, to: LocalDate) {
    logger.info { "Aggregating $type from $from to $to for user: ${userId.value}" }

    val dailyAggregations = aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, from, to)
    if (dailyAggregations.isEmpty()) {
      logger.info { "No daily aggregations found for $from to $to, user: ${userId.value} — saving empty $type aggregation" }
      aggregationRepository.save(emptyAggregation(userId, type, from))
      return
    }

    val mergedArtistEntries = dailyAggregations.flatMap { it.artistEntries }
      .groupBy { it.id }
      .map { (id, entries) -> AggregationRankEntry(id = id, name = entries.first().name, totalSeconds = entries.sumOf { it.totalSeconds }) }
      .sortedByDescending { it.totalSeconds }

    val mergedTrackEntries = dailyAggregations.flatMap { it.trackEntries }
      .groupBy { it.id }
      .map { (id, entries) -> AggregationRankEntry(id = id, name = entries.first().name, totalSeconds = entries.sumOf { it.totalSeconds }) }
      .sortedByDescending { it.totalSeconds }

    val mergedActivityEntries = dailyAggregations.flatMap { it.activityEntries }
      .groupBy { it.dayOfWeek to it.timeWindow }
      .map { (key, entries) -> ActivityEntry(dayOfWeek = key.first, timeWindow = key.second, totalSeconds = entries.sumOf { it.totalSeconds }) }

    val aggregation = PlaybackAggregation(
      userId = userId,
      type = type,
      periodStart = from,
      totalPlaybackSeconds = dailyAggregations.sumOf { it.totalPlaybackSeconds },
      distinctArtistCount = mergedArtistEntries.size,
      distinctTrackCount = mergedTrackEntries.size,
      artistEntries = mergedArtistEntries,
      trackEntries = mergedTrackEntries,
      activityEntries = mergedActivityEntries,
    )
    aggregationRepository.save(aggregation)
    logger.info { "Saved $type aggregation for $from, user: ${userId.value}" }
  }

  private fun emptyAggregation(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation = PlaybackAggregation(
    userId = userId,
    type = type,
    periodStart = periodStart,
    totalPlaybackSeconds = 0L,
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    artistEntries = emptyList(),
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )

  companion object : KLogging() {
    private const val UNKNOWN_ARTIST_ID = "unknown"
  }
}

private fun LocalDate.endOfMonth(): LocalDate {
  val javaDate = toJavaLocalDate()
  val lastDay = javaDate.withDayOfMonth(javaDate.lengthOfMonth())
  return LocalDate(lastDay.year, lastDay.monthValue, lastDay.dayOfMonth)
}

private fun LocalDate.plusKDays(days: Long): LocalDate {
  val javaDate = toJavaLocalDate().plusDays(days)
  return LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
}

private fun LocalDate.plusKMonths(months: Long): LocalDate {
  val javaDate = toJavaLocalDate().plusMonths(months)
  return LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
}

private fun LocalDate.minusKDays(days: Long): LocalDate {
  val javaDate = toJavaLocalDate().minusDays(days)
  return LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
}
