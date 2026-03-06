package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItemComputed
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant

interface ComputedRecentlyPlayedRepositoryPort {
    fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant>
    fun saveAll(items: List<RecentlyPlayedItemComputed>)
}
