package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
    fun handle(event: DomainOutboxEvent.RunPlaylistChecks): OutboxTaskResult
    fun getDisplayNames(): Map<String, String>
}
