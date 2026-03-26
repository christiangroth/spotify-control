package de.chrgroth.spotify.control.domain.port.`in`.playback

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaybackPort {
    fun enqueueFetchCurrentlyPlaying()
    fun fetchCurrentlyPlaying(userId: UserId): Either<DomainError, Unit>
    fun enqueueFetchRecentlyPlayed()
    fun fetchRecentlyPlayed(userId: UserId): Either<DomainError, Unit>
    fun enqueueRebuildPlaybackData(userId: UserId)
    fun rebuildPlaybackData(userId: UserId)
    fun appendPlaybackData(userId: UserId)
    fun syncArtistPlaybackFromPlaylists(userId: UserId)
    fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit>
    fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit>
}
