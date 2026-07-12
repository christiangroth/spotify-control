package de.chrgroth.spotify.control.domain.model.catalog

/**
 * SYNC and SHALLOW are final states, set explicitly by the user via the catalog UI.
 * SYNC_ASSUMPTION and SHALLOW_ASSUMPTION are the automatic guess made when a previously unknown artist is
 * discovered; they can only transition into one another or into one of the final states.
 */
enum class ArtistSyncStatus {
  SYNC,
  SHALLOW,
  SYNC_ASSUMPTION,
  SHALLOW_ASSUMPTION,
}
