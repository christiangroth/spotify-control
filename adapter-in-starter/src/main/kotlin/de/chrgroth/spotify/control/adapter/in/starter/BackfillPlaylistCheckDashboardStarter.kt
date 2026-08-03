package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import jakarta.enterprise.context.ApplicationScoped

// Builds the app_playlist_check_dashboard read model once for existing deployments (see #867 Slow Queries,
// ADR-0014) so PlaylistChecksResource/PlaylistsResource can read a single precomputed document instead of
// recomputing the dashboard on every request. Later updates happen inline whenever RunPlaylistChecks is handled.
@ApplicationScoped
@Suppress("Unused")
class BackfillPlaylistCheckDashboardStarter(
  private val playlistCheckPort: PlaylistCheckPort,
) : Starter {

  override val id = "BackfillPlaylistCheckDashboardStarter-v1"

  override fun execute() {
    playlistCheckPort.rebuildCheckDashboard()
  }
}
