package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId
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
    fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): DispatchResult
    fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): DispatchResult
    fun handle(event: DomainOutboxEvent.RebuildPlaybackData): DispatchResult
    fun handle(event: DomainOutboxEvent.AppendPlaybackData): DispatchResult
}
