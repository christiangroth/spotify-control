package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck

interface AppPlaylistCheckRepositoryPort {
    fun save(check: AppPlaylistCheck)
    fun findByCheckId(checkId: String): AppPlaylistCheck?
    fun findAll(): List<AppPlaylistCheck>
    fun countAll(): Long
    fun countSucceeded(): Long
    fun deleteByPlaylistId(playlistId: String)
    fun deleteAll()
}
