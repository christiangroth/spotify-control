package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import kotlin.time.Instant

interface RecentlyPartialPlayedRepositoryPort {
  fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant>
  fun findSince(since: Instant?): List<RecentlyPartialPlayedItem>
  fun findByTrackIds(trackIds: Set<TrackId>): List<RecentlyPartialPlayedItem>
  fun saveAll(items: List<RecentlyPartialPlayedItem>)
  fun deleteByPlayedAts(playedAts: Set<Instant>)
}
