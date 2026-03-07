package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppArtist

interface AppArtistRepositoryPort {
    fun upsertAll(items: List<AppArtist>)
    fun findByArtistIds(artistIds: Set<String>): List<AppArtist>
    fun updateEnrichmentData(artistId: String, genres: List<String>, imageLink: String?)
}
