package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  /**
   * Mixes artist-scoped ([de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent.SyncArtistDetails],
   * [de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent.SyncArtistAlbums]) and album-scoped
   * ([de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent.SyncAlbumDetails]) tasks. All events carry
   * a groupId (artistId/albumId), so different artists/albums sync concurrently across [workerCount] workers
   * while per-entity ordering is preserved.
   */
  data object ToSpotifyCatalog : DomainOutboxPartition {
    override val key = "to-spotify-catalog"
    override val workerCount = 3
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

  /**
   * Catch-all partition for internal domain events: per-entity work (artist sync confirmations, playlist
   * checks), playback aggregation, and precomputed read-model rebuilds (see ADR-0014). None of these are
   * ordering-dependent on one another, so every event carries a groupId — the relevant entity id where one
   * exists, otherwise the event's own key — allowing unrelated groups to run concurrently across
   * [workerCount] workers while same-group events (e.g. two syncs for the same artist) still process in order.
   */
  data object Domain : DomainOutboxPartition {
    override val key = "domain"
    override val workerCount = 4
  }

  companion object {
    val all: List<DomainOutboxPartition> = listOf(ToSpotifyCatalog, ToSpotifyPlayback, ToSpotifyPlaylist, ToSpotifyUser, Domain)
  }
}
