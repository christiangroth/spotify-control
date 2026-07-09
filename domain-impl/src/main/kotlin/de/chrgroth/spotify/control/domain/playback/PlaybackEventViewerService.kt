package de.chrgroth.spotify.control.domain.playback

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventEntry
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventType
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.playback.RawPlaybackEvent
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackEventViewerPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackEventViewerRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@ApplicationScoped
@Suppress("Unused")
class PlaybackEventViewerService(
  private val repository: PlaybackEventViewerRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
) : PlaybackEventViewerPort {

  override fun getEvents(date: LocalDate): PlaybackEventViewerResult {
    val tz = TimeZone.currentSystemDefault()
    val isToday = date == Clock.System.now().toLocalDateTime(tz).date
    val userId = currentUserResolver.userId() ?: return PlaybackEventViewerResult(date = date, isToday = isToday, events = emptyList())
    val from = date.atStartOfDayIn(tz)
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(tz)

    val rawCurrentlyPlaying = repository.findCurrentlyPlaying(userId, from, to)
    val latestCurrentlyPlayingTimestamp = if (isToday) rawCurrentlyPlaying.maxByOrNull { it.timestamp }?.timestamp else null

    val allEvents = (
      repository.findRecentlyPlayed(userId, from, to).map { it.toEntry(PlaybackEventType.RECENTLY_PLAYED, false) } +
        repository.findRecentlyPartialPlayed(userId, from, to).map { it.toEntry(PlaybackEventType.RECENTLY_PARTIAL_PLAYED, false) } +
        rawCurrentlyPlaying.map { it.toEntry(PlaybackEventType.CURRENTLY_PLAYING, it.timestamp != latestCurrentlyPlayingTimestamp) }
      ).sortedByDescending { it.timestamp }

    return PlaybackEventViewerResult(date = date, isToday = isToday, events = enrichWithAlbumNames(allEvents))
  }

  private fun RawPlaybackEvent.toEntry(type: PlaybackEventType, isWarning: Boolean) = PlaybackEventEntry(
    type = type,
    timestamp = timestamp,
    isWarning = isWarning,
    trackId = trackId,
    trackName = trackName,
    artistId = artistIds.firstOrNull(),
    artistName = artistNames.firstOrNull(),
    albumId = albumId,
    albumName = null,
    startTime = startTime,
    durationSeconds = durationSeconds,
  )

  private fun enrichWithAlbumNames(events: List<PlaybackEventEntry>): List<PlaybackEventEntry> {
    val albumIds = events.mapNotNull { it.albumId }.toSet().map { AlbumId(it) }.toSet()
    if (albumIds.isEmpty()) return events
    val albumNameById = appAlbumRepository.findByAlbumIds(albumIds).associate { it.id.value to it.title }
    return events.map { it.copy(albumName = it.albumId?.let { id -> albumNameById[id] }) }
  }
}
