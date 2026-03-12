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
    fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit>
    fun enrichTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit>
    fun enrichTrackDetailsBulk(trackIds: List<String>, userId: UserId): Either<DomainError, Unit>
    fun enrichAlbumDetails(albumId: String, userId: UserId): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.EnrichArtistDetails): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.EnrichTrackDetails): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.EnrichTrackDetailsBulk): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.EnrichAlbumDetails): OutboxTaskResult
}
