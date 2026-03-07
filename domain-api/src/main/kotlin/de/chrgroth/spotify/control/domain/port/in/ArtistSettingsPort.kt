package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId

interface ArtistSettingsPort {
    fun findAllArtists(): List<AppArtist>
    fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit>
}
