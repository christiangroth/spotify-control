package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

interface AppPlaybackRepositoryPort {
    fun saveAll(items: List<AppPlaybackItem>)
    fun deleteAllByUserId(userId: UserId)
    fun findMostRecentPlayedAt(userId: UserId): Instant?
    fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant>
    fun countAll(userId: UserId): Long
    fun countSince(userId: UserId, since: Instant): Long
    fun countPerDaySince(userId: UserId, since: Instant): List<Pair<LocalDate, Long>>
    fun findRecentlyPlayed(userId: UserId, limit: Int): List<AppPlaybackItem>
    fun findAllSince(userId: UserId, since: Instant): List<AppPlaybackItem>
}
