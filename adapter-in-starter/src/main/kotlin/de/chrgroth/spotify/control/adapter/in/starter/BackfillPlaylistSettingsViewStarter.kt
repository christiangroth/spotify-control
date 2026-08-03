package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import jakarta.enterprise.context.ApplicationScoped

// Builds the app_playlist_settings_view read model once for existing deployments (see #867 Slow Queries,
// ADR-0014) so PlaylistsResource can read a single precomputed document instead of recomputing the settings
// tab on every request. Later updates happen inline whenever playlist/artist sync events are handled.
@ApplicationScoped
@Suppress("Unused")
class BackfillPlaylistSettingsViewStarter(
  private val playlistPort: PlaylistPort,
) : Starter {

  override val id = "BackfillPlaylistSettingsViewStarter-v1"

  override fun execute() {
    playlistPort.rebuildPlaylistSettingsView()
  }
}
