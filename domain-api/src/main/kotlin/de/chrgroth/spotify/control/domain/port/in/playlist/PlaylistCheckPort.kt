package de.chrgroth.spotify.control.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboard
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
  fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.FixPlaylistCheck): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.RebuildPlaylistChecksDashboard): Either<DomainError, Unit>
  fun getCheckDashboard(): PlaylistCheckDashboard
  fun rebuildCheckDashboard()
  fun getDisplayNames(): Map<String, String>
  fun getFixableCheckIds(): Set<String>
  fun enqueueFix(playlistId: String, checkType: String, violationIds: Set<String>): Either<DomainError, Unit>
  fun enqueueRunAllChecks()
  fun enqueueRunCheck(checkType: String)
}
