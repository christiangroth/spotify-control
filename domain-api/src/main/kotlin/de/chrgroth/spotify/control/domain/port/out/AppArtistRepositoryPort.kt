package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus

interface AppArtistRepositoryPort {
    fun upsertAll(items: List<AppArtist>)
    fun findAll(): List<AppArtist>
    fun findByArtistIds(artistIds: Set<String>): List<AppArtist>
    fun findByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): List<AppArtist>
    fun updateEnrichmentData(artistId: String, artistName: String, genre: String?, additionalGenres: List<String>?, imageLink: String?, type: String?)
    fun findWithImageLinkAndBlankName(): List<AppArtist>
    fun updatePlaybackProcessingStatus(artistId: String, status: ArtistPlaybackProcessingStatus)
}
