package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant

interface RecentlyPlayedRepositoryPort {
    fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant>
    fun saveAll(items: List<RecentlyPlayedItem>)
}
