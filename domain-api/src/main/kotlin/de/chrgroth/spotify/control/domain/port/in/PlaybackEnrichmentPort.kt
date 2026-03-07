package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId

interface PlaybackEnrichmentPort {
    fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit>
    fun enrichTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit>
}
