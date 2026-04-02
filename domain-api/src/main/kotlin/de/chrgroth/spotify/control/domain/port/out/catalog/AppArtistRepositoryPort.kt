package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistPlaybackProcessingStatus

interface AppArtistRepositoryPort {
  fun upsertAll(items: List<AppArtist>)
  fun countAll(): Long
  fun findAll(): List<AppArtist>
  fun findByArtistIds(artistIds: Set<ArtistId>): List<AppArtist>
  fun findByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): List<AppArtist>
  fun findByPlaybackProcessingStatusPaged(status: ArtistPlaybackProcessingStatus, offset: Int, limit: Int): List<AppArtist>
  fun countByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): Long
  fun findWithImageLinkAndBlankName(): List<AppArtist>
  fun updatePlaybackProcessingStatus(artistId: ArtistId, status: ArtistPlaybackProcessingStatus)
  fun deleteAll()
}
