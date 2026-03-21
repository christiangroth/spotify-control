package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.quarkus.outbox.domain.DispatchResult
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
    fun handle(event: DomainOutboxEvent.RunPlaylistChecks): DispatchResult
}
