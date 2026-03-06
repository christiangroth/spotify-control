package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant

interface RecentlyPartialPlayedRepositoryPort {
    fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant>
    fun saveAll(items: List<RecentlyPartialPlayedItem>)
}
