package de.chrgroth.spotify.control.domain.port.`in`.playback

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaybackPort {
  fun enqueueFetchPlaybackData()
  fun fetchPlaybackData(): Either<DomainError, Unit>
  fun enqueueRebuildPlaybackData()
  fun rebuildPlaybackData()
  fun appendPlaybackData()
  fun handle(event: DomainOutboxEvent.FetchPlaybackData): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit>
}
