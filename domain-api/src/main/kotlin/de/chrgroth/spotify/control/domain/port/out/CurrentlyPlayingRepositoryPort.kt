package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.UserId

interface CurrentlyPlayingRepositoryPort {
    fun save(item: CurrentlyPlayingItem)
    fun existsByUserAndTrackAndObservedMinute(item: CurrentlyPlayingItem): Boolean
    fun updateProgressByUserAndTrackAndObservedMinute(item: CurrentlyPlayingItem)
    fun findByUserAndTrack(userId: UserId, trackId: String): List<CurrentlyPlayingItem>
    fun findByUserId(userId: UserId): List<CurrentlyPlayingItem>
    fun deleteByUserIdAndTrackIds(userId: UserId, trackIds: Set<String>)
}
