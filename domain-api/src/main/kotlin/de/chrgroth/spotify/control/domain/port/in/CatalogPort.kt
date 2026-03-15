package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface CatalogPort {
    fun findAllArtists(): List<AppArtist>
    fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit>
    fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit>
    fun syncTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit>
    fun resyncCatalog(): Either<DomainError, Unit>
    fun resyncArtist(artistId: String): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.SyncArtistDetails): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.SyncTrackDetails): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.SyncMissingAlbums): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.ResyncCatalog): OutboxTaskResult
}
