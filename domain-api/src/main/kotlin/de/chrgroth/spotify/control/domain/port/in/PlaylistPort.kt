package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistPort {
    fun enqueueUpdates()
    fun syncPlaylists(userId: UserId): Either<DomainError, Unit>
    fun syncPlaylistData(userId: UserId, playlistId: String, nextUrl: String? = null): Either<DomainError, Unit>
    fun updateSyncStatus(userId: UserId, playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit>
    fun updatePlaylistType(userId: UserId, playlistId: String, type: PlaylistType): Either<DomainError, Unit>
    fun enqueueSyncPlaylistData(userId: UserId, playlistId: String): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.SyncPlaylistData): OutboxTaskResult
}
