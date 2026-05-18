package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  data object ToSpotifyCatalogArtist : DomainOutboxPartition {
    override val key = "to-spotify-catalog-artist"
  }

  data object ToSpotifyCatalogAlbum : DomainOutboxPartition {
    override val key = "to-spotify-catalog-album"
  }

  data object ToSpotifyPlayback : DomainOutboxPartition {
    override val key = "to-spotify-playback"
  }

  data object ToSpotifyUser : DomainOutboxPartition {
    override val key = "to-spotify-user"
  }

  data object ToSpotifyPlaylist : DomainOutboxPartition {
    override val key = "to-spotify-playlist"
  }

  data object Domain : DomainOutboxPartition {
    override val key = "domain"
  }

  companion object {
    val all: List<DomainOutboxPartition> = listOf(ToSpotifyCatalogArtist, ToSpotifyCatalogAlbum, ToSpotifyPlayback, ToSpotifyPlaylist, ToSpotifyUser, Domain)
  }
}
