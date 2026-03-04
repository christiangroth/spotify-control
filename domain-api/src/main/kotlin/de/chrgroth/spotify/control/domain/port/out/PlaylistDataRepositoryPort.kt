package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.UserId

interface PlaylistDataRepositoryPort {
    fun findByUserIdAndPlaylistId(userId: UserId, playlistId: String): Playlist?
    fun save(userId: UserId, playlist: Playlist)
}
