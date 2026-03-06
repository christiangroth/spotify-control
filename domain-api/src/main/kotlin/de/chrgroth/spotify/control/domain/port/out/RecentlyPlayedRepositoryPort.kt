package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

interface RecentlyPlayedRepositoryPort {
    fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant>
    fun findMostRecentPlayedAt(spotifyUserId: UserId): Instant?
    fun saveAll(items: List<RecentlyPlayedItem>)
    fun countAll(spotifyUserId: UserId): Long
    fun countSince(spotifyUserId: UserId, since: Instant): Long
    fun countPerDaySince(spotifyUserId: UserId, since: Instant): List<Pair<LocalDate, Long>>
    fun deleteNonTracks(): Long
}
