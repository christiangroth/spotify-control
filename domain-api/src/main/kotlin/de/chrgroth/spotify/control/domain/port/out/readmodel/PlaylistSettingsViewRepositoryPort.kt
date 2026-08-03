package de.chrgroth.spotify.control.domain.port.out.readmodel

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView

interface PlaylistSettingsViewRepositoryPort {
  fun save(view: PlaylistSettingsView)
  fun find(): PlaylistSettingsView?
}
