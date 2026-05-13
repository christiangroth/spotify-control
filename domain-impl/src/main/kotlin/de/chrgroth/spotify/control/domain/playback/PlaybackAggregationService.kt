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
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import mu.KLogging
import java.time.LocalDate as JLocalDate

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

  // --- Rebuild ---

  override fun rebuildAllAggregations() {
    val oldestInstant = appPlaybackRepository.findOldestPlayedAt()
    if (oldestInstant == null) {
      logger.info { "No playback data found, skipping aggregation rebuild" }
      return
    }

    val firstDate = JLocalDate.ofInstant(oldestInstant.toJavaInstant(), ZoneOffset.UTC)
    val today = JLocalDate.now(ZoneOffset.UTC)
    val yesterday = today.minusDays(1)

    logger.info { "Deleting all aggregation data before rebuild" }
    aggregationRepository.deleteAll()

    logger.info { "Rebuilding aggregations from $firstDate to $yesterday" }

    enqueueDays(firstDate, yesterday)
    enqueueWeeks(firstDate, yesterday)
    enqueueMonths(firstDate, yesterday)
    enqueueQuarters(firstDate, yesterday)
    enqueueYears(firstDate, yesterday)

    logger.info { "Aggregation rebuild enqueuing complete" }
  }

  private fun enqueueDays(from: JLocalDate, to: JLocalDate) {
    var date = from
    while (!date.isAfter(to)) {
      enqueueAggregateDay(date.toKotlin())
      date = date.plusDays(1)
    }
    logger.info { "Enqueued daily aggregations from $from to $to" }
  }

  private fun enqueueWeeks(from: JLocalDate, to: JLocalDate) {
    var weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastCompleteWeekStart = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var count = 0
    while (!weekStart.isAfter(lastCompleteWeekStart)) {
      enqueueAggregateWeek(weekStart.toKotlin())
      weekStart = weekStart.plusWeeks(1)
      count++
    }
    logger.info { "Enqueued $count weekly aggregation(s)" }
  }

  private fun enqueueMonths(from: JLocalDate, to: JLocalDate) {
    var monthStart = from.withDayOfMonth(1)
    val lastCompleteMonthStart = to.withDayOfMonth(1)
    var count = 0
    while (!monthStart.isAfter(lastCompleteMonthStart)) {
      enqueueAggregateMonth(monthStart.toKotlin())
      monthStart = monthStart.plusMonths(1)
      count++
    }
    logger.info { "Enqueued $count monthly aggregation(s)" }
  }

  private fun enqueueQuarters(from: JLocalDate, to: JLocalDate) {
    var quarterStart = firstDayOfQuarter(from)
    val lastCompleteQuarterStart = firstDayOfQuarter(to)
    var count = 0
    while (!quarterStart.isAfter(lastCompleteQuarterStart)) {
      enqueueAggregateQuarter(quarterStart.toKotlin())
      quarterStart = quarterStart.plusMonths(MONTHS_PER_QUARTER)
      count++
    }
    logger.info { "Enqueued $count quarterly aggregation(s)" }
  }

  private fun enqueueYears(from: JLocalDate, to: JLocalDate) {
    var yearStart = from.withDayOfYear(1)
    val lastCompleteYearStart = to.withDayOfYear(1)
    var count = 0
    while (!yearStart.isAfter(lastCompleteYearStart)) {
      enqueueAggregateYear(yearStart.toKotlin())
      yearStart = yearStart.plusYears(1)
      count++
    }
    logger.info { "Enqueued $count yearly aggregation(s)" }
  }

  private fun firstDayOfQuarter(date: JLocalDate): JLocalDate {
    val firstMonthOfQuarter = ((date.monthValue - 1) / MONTHS_PER_QUARTER.toInt()) * MONTHS_PER_QUARTER.toInt() + 1
    return JLocalDate.of(date.year, firstMonthOfQuarter, 1)
  }

  // --- Outbox handler ---

  override fun handle(event: DomainOutboxEvent.AggregatePlaybackData): Either<DomainError, Unit> {
    when (event.type) {
      AggregationPeriodType.DAY -> aggregateDay(event.userId, event.periodStart)
      AggregationPeriodType.WEEK -> aggregateFromDailyAggregations(
        event.userId, AggregationPeriodType.WEEK, event.periodStart, event.periodStart.plusKDays(DAYS_IN_WEEK),
      )
      AggregationPeriodType.MONTH -> aggregateFromDailyAggregations(
        event.userId, AggregationPeriodType.MONTH, event.periodStart, event.periodStart.endOfMonth(),
      )
      AggregationPeriodType.QUARTER -> aggregateFromDailyAggregations(
        event.userId, AggregationPeriodType.QUARTER, event.periodStart, event.periodStart.plusKMonths(MONTHS_PER_QUARTER).minusKDays(1),
      )
      AggregationPeriodType.YEAR -> aggregateFromDailyAggregations(
        event.userId, AggregationPeriodType.YEAR, event.periodStart, event.periodStart.plusKMonths(MONTHS_PER_YEAR).minusKDays(1),
      )
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

    val artistIds = tracks.values.mapNotNull { it.artistId }.toSet()
    val artists = appArtistRepository.findByArtistIds(artistIds).associateBy { it.id }
    val blockedArtistIds = artists.values.filter { it.blockedFromAggregation }.map { it.id }.toSet()

    val filteredItems = items.filter { item ->
      val artistId = tracks[TrackId(item.trackId)]?.artistId
      artistId == null || artistId !in blockedArtistIds
    }

    if (filteredItems.isEmpty()) {
      logger.info { "All playback items for $date, user: ${userId.value} are from blocked artists — saving empty aggregation" }
      aggregationRepository.save(emptyAggregation(userId, AggregationPeriodType.DAY, date))
      return
    }

    val rankings = buildRankings(filteredItems, tracks, artists)
    val activityEntries = buildActivityEntries(filteredItems)

    val aggregation = PlaybackAggregation(
      userId = userId,
      type = AggregationPeriodType.DAY,
      periodStart = date,
      totalPlaybackSeconds = filteredItems.sumOf { it.secondsPlayed },
      eventCount = filteredItems.size.toLong(),
      distinctArtistCount = rankings.artistEntries.size,
      distinctTrackCount = rankings.trackEntries.size,
      distinctAlbumCount = rankings.albumEntries.size,
      artistEntries = rankings.artistEntries,
      albumEntries = rankings.albumEntries,
      trackEntries = rankings.trackEntries,
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

    val mergedAlbumEntries = dailyAggregations.flatMap { it.albumEntries }
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
      eventCount = dailyAggregations.sumOf { it.eventCount },
      distinctArtistCount = mergedArtistEntries.size,
      distinctTrackCount = mergedTrackEntries.size,
      distinctAlbumCount = mergedAlbumEntries.size,
      artistEntries = mergedArtistEntries,
      albumEntries = mergedAlbumEntries,
      trackEntries = mergedTrackEntries,
      activityEntries = mergedActivityEntries,
    )
    aggregationRepository.save(aggregation)
    logger.info { "Saved $type aggregation for $from, user: ${userId.value}" }
  }

  private fun buildRankings(
    filteredItems: List<de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem>,
    tracks: Map<TrackId, de.chrgroth.spotify.control.domain.model.catalog.AppTrack>,
    artists: Map<ArtistId, de.chrgroth.spotify.control.domain.model.catalog.AppArtist>,
  ): Rankings {
    val durationPerTrackId = filteredItems.groupBy { it.trackId }.mapValues { (_, entries) -> entries.sumOf { it.secondsPlayed } }
    val durationPerArtistId = mutableMapOf<String, Long>()
    val durationPerAlbumId = mutableMapOf<String, Long>()
    val albumNamesById = mutableMapOf<String, String>()
    filteredItems.forEach { item ->
      val track = tracks[TrackId(item.trackId)]
      val artistId = track?.artistId?.value ?: UNKNOWN_ARTIST_ID
      val albumId = track.albumAggregationId(item.trackId)
      val albumName = track?.albumName ?: track?.title ?: albumId
      durationPerArtistId[artistId] = (durationPerArtistId[artistId] ?: 0L) + item.secondsPlayed
      durationPerAlbumId[albumId] = (durationPerAlbumId[albumId] ?: 0L) + item.secondsPlayed
      albumNamesById.putIfAbsent(albumId, albumName)
    }
    val trackEntries = durationPerTrackId.map { (trackId, seconds) ->
      AggregationRankEntry(id = trackId, name = tracks[TrackId(trackId)]?.title ?: trackId, totalSeconds = seconds)
    }.sortedByDescending { it.totalSeconds }
    val artistEntries = durationPerArtistId.map { (artistId, seconds) ->
      AggregationRankEntry(id = artistId, name = artists[ArtistId(artistId)]?.artistName ?: artistId, totalSeconds = seconds)
    }.sortedByDescending { it.totalSeconds }
    val albumEntries = durationPerAlbumId.map { (albumId, seconds) ->
      AggregationRankEntry(id = albumId, name = albumNamesById[albumId] ?: albumId, totalSeconds = seconds)
    }.sortedByDescending { it.totalSeconds }
    return Rankings(trackEntries = trackEntries, artistEntries = artistEntries, albumEntries = albumEntries)
  }

  private fun buildActivityEntries(filteredItems: List<de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem>): List<ActivityEntry> =
    filteredItems.groupBy { item ->
      val zdt = item.playedAt.toJavaInstant().atZone(ZoneOffset.UTC)
      zdt.dayOfWeek to ActivityTimeWindow.fromHour(zdt.hour)
    }.map { (key, entries) ->
      ActivityEntry(dayOfWeek = key.first, timeWindow = key.second, totalSeconds = entries.sumOf { it.secondsPlayed })
    }

  private fun emptyAggregation(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation = PlaybackAggregation(
    userId = userId,
    type = type,
    periodStart = periodStart,
    totalPlaybackSeconds = 0L,
    eventCount = 0L,
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    distinctAlbumCount = 0,
    artistEntries = emptyList(),
    albumEntries = emptyList(),
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )

  companion object : KLogging() {
    private const val UNKNOWN_ARTIST_ID = "unknown"
    private const val FALLBACK_ALBUM_ID_PREFIX = "fallback:"
    private const val DAYS_IN_WEEK = 6L
    private const val MONTHS_PER_QUARTER = 3L
    private const val MONTHS_PER_YEAR = 12L
  }

  private data class Rankings(
    val trackEntries: List<AggregationRankEntry>,
    val artistEntries: List<AggregationRankEntry>,
    val albumEntries: List<AggregationRankEntry>,
  )

  private fun de.chrgroth.spotify.control.domain.model.catalog.AppTrack?.albumAggregationId(itemTrackId: String): String {
    val rawAlbumId = this?.albumId?.value
    if (rawAlbumId != null) {
      return rawAlbumId
    }
    return "${FALLBACK_ALBUM_ID_PREFIX}${this?.albumName ?: itemTrackId}"
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

private fun JLocalDate.toKotlin(): LocalDate = LocalDate(year, monthValue, dayOfMonth)
