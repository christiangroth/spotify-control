package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId

interface PlaybackEnrichmentPort {
    fun enrichArtistData(userId: UserId): Either<DomainError, Unit>
    fun enrichTrackData(userId: UserId): Either<DomainError, Unit>
}
