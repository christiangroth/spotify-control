package de.chrgroth.spotify.control.domain.playback

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventEntry
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventType
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackEventViewerPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackEventViewerRepositoryPort
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
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
) : PlaybackEventViewerPort {

  override fun getEvents(userId: UserId, date: LocalDate): PlaybackEventViewerResult {
    val tz = TimeZone.currentSystemDefault()
    val from = date.atStartOfDayIn(tz)
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(tz)
    val today = Clock.System.now().toLocalDateTime(tz).date
    val isToday = date == today

    val recentlyPlayed = repository.findRecentlyPlayed(userId, from, to)
      .map { PlaybackEventEntry(PlaybackEventType.RECENTLY_PLAYED, it.timestamp, it.json, false) }

    val partialPlayed = repository.findRecentlyPartialPlayed(userId, from, to)
      .map { PlaybackEventEntry(PlaybackEventType.RECENTLY_PARTIAL_PLAYED, it.timestamp, it.json, false) }

    val rawCurrentlyPlaying = repository.findCurrentlyPlaying(userId, from, to)
    val latestCurrentlyPlayingTimestamp = if (isToday) rawCurrentlyPlaying.maxByOrNull { it.timestamp }?.timestamp else null
    val currentlyPlayingEntries = rawCurrentlyPlaying.map {
      PlaybackEventEntry(
        type = PlaybackEventType.CURRENTLY_PLAYING,
        timestamp = it.timestamp,
        json = it.json,
        isWarning = it.timestamp != latestCurrentlyPlayingTimestamp,
      )
    }

    val allEvents = (recentlyPlayed + partialPlayed + currentlyPlayingEntries).sortedByDescending { it.timestamp }

    val playbackItems = appPlaybackRepository.findAllBetween(userId, from, to)
    val trackIds = playbackItems.map { TrackId(it.trackId) }.toSet()
    val tracks = appTrackRepository.findByTrackIds(trackIds)
    val artistIds = tracks.mapNotNull { it.artistId }.toSet()
    val artists = appArtistRepository.findByArtistIds(artistIds)
      .sortedBy { it.artistName.lowercase() }

    return PlaybackEventViewerResult(date = date, isToday = isToday, events = allEvents, artists = artists)
  }
}
