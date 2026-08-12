package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import kotlin.time.Instant

interface RecentlyPlayedRepositoryPort {
  fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant>
  fun findMostRecentPlayedAt(): Instant?
  fun findSince(since: Instant?): List<RecentlyPlayedItem>
  fun findArtistNamesByIds(artistIds: Set<ArtistId>): Map<ArtistId, String>
  fun saveAll(items: List<RecentlyPlayedItem>)
  fun deleteNonTracks(): Long
}
