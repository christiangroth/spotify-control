package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppTrackData

interface AppTrackDataRepositoryPort {
    fun upsertAll(items: List<AppTrackData>)
    fun findByTrackIds(trackIds: Set<String>): List<AppTrackData>
}
