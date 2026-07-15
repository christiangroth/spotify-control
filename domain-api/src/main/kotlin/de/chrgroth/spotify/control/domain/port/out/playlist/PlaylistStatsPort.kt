package de.chrgroth.spotify.control.domain.port.out.playlist

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistStats

interface PlaylistStatsPort {
  fun current(): PlaylistStats
}
