package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppTrack

interface AppTrackRepositoryPort {
    fun upsertAll(items: List<AppTrack>)
    fun findByTrackIds(trackIds: Set<String>): List<AppTrack>
}
