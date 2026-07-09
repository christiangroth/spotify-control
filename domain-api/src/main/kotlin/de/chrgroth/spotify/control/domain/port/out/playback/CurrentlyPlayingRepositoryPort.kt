package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem

interface CurrentlyPlayingRepositoryPort {
  fun save(item: CurrentlyPlayingItem)
  fun findMostRecentByTrack(trackId: TrackId): CurrentlyPlayingItem?
  fun updateProgress(item: CurrentlyPlayingItem)
  fun findAll(): List<CurrentlyPlayingItem>
  fun deleteByTrackIds(trackIds: Set<String>)
}
