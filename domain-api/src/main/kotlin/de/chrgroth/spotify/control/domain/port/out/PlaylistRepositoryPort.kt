package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.UserId

interface PlaylistRepositoryPort {
    fun findByUserId(userId: UserId): List<PlaylistInfo>
    fun saveAll(userId: UserId, playlists: List<PlaylistInfo>)
    fun findByUserIdAndPlaylistId(userId: UserId, playlistId: String): Playlist?
    fun save(userId: UserId, playlist: Playlist)
}
