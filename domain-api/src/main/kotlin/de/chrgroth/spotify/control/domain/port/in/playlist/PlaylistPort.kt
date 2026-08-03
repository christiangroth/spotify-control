package de.chrgroth.spotify.control.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.ArtistStats
import de.chrgroth.spotify.control.domain.model.playlist.MissingArtist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistPort {
  fun getPlaylists(): List<PlaylistInfo>
  fun getTrackCounts(): Map<String, Int>
  fun getArtistStats(): Map<String, ArtistStats>
  fun getMissingArtists(playlistId: String): List<MissingArtist>
  fun getPlaylistSettingsView(): PlaylistSettingsView
  fun rebuildPlaylistSettingsView()
  fun enqueueUpdates()
  fun syncPlaylists(): Either<DomainError, Unit>
  fun syncPlaylistData(playlistId: String, nextUrl: String? = null, snapshotId: String? = null): Either<DomainError, Unit>
  fun updateSyncStatus(playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit>
  fun updatePlaylistType(playlistId: String, type: PlaylistType): Either<DomainError, Unit>
  fun enqueueSyncPlaylistData(playlistId: String): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncPlaylistData): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.RebuildPlaylistSettingsView): Either<DomainError, Unit>
}
