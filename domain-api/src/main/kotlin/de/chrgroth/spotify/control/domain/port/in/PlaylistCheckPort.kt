package de.chrgroth.spotify.control.domain.port.`in`

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
  fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit>
  fun getDisplayNames(): Map<String, String>
}
