package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult

interface OutboxHandlerPort {
    fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.UpdateUserProfile): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): OutboxTaskResult
    fun handle(event: DomainOutboxEvent.SyncPlaylistData): OutboxTaskResult
}
