package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.TrackId

interface AppTrackRepositoryPort {
    fun upsertAll(items: List<AppTrack>)
    fun findAll(): List<AppTrack>
    fun findByTrackIds(trackIds: Set<TrackId>): List<AppTrack>
    fun findByArtistId(artistId: ArtistId): List<AppTrack>
    fun updateTrackSyncData(track: AppTrack)
}
