package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

interface AppPlaybackRepositoryPort {
  fun saveAll(items: List<AppPlaybackItem>)
  fun deleteAll()
  fun deleteAllByTrackIds(trackIds: Set<String>)
  fun deleteByPlayedAts(playedAts: Set<Instant>)
  fun findMostRecentPlayedAt(): Instant?
  fun findOldestPlayedAt(): Instant?
  fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant>
  fun countAll(): Long
  fun countSince(since: Instant): Long
  fun countPerDaySince(since: Instant): List<Pair<LocalDate, Long>>
  fun findRecentlyPlayed(limit: Int): List<AppPlaybackItem>
  fun findAllSince(since: Instant): List<AppPlaybackItem>
  fun findAllBetween(from: Instant, to: Instant): List<AppPlaybackItem>
  fun sumSecondsPlayedByTrackIdSince(since: Instant): Map<String, Long>
  fun findAllDistinctTrackIds(): Set<String>
  fun findDistinctPlayedDatesByTrackIds(trackIds: Set<String>): Set<LocalDate>
}
