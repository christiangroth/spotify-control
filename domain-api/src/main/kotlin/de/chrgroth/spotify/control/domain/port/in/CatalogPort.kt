package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface CatalogPort {
    fun findAllArtists(): List<AppArtist>
    fun findArtistsByStatus(status: ArtistPlaybackProcessingStatus, offset: Int, limit: Int): List<AppArtist>
    fun countArtistsByStatus(status: ArtistPlaybackProcessingStatus): Long
    fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit>
    fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit>
    fun resyncCatalog(): Either<DomainError, Unit>
    fun resyncArtist(artistId: String): Either<DomainError, Unit>
    fun wipeCatalog(): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit>
}
