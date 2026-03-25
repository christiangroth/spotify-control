package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.UserId

interface PlaylistCheckRunner {
    val checkId: String
    val displayName: String
    fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = true
    fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck
}
