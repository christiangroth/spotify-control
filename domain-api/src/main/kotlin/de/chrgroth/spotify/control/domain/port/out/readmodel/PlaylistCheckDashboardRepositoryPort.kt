package de.chrgroth.spotify.control.domain.port.out.readmodel

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboardSummary

interface PlaylistCheckDashboardRepositoryPort {
  fun save(summary: PlaylistCheckDashboardSummary)
  fun find(): PlaylistCheckDashboardSummary?
}
