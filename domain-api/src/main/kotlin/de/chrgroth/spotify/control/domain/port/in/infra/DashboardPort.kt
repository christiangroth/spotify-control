package de.chrgroth.spotify.control.domain.port.`in`.infra

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface DashboardPort {
  fun getStats(): DashboardStats
  fun getPlaybackStats(): DashboardStats
  fun getPlaylistMetadata(): DashboardStats
  fun getRecentlyPlayed(): DashboardStats
  fun getListeningStats(): DashboardStats
  fun getPlaylistCheckStats(): DashboardStats
  fun getCatalogStats(): DashboardStats
  fun rebuildDashboardView()
  fun handle(event: DomainOutboxEvent.RebuildDashboardReadModel): Either<DomainError, Unit>
}
