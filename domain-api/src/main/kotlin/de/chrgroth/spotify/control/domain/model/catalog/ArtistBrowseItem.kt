package de.chrgroth.spotify.control.domain.model.catalog

data class ArtistBrowseItem(
  val artistId: String,
  val artistName: String,
  val imageLink: String?,
  val albumCount: Int,
  val trackCount: Int,
  val syncStatus: ArtistSyncStatus = ArtistSyncStatus.SYNC,
) {
  val isShallow: Boolean get() = syncStatus == ArtistSyncStatus.SHALLOW || syncStatus == ArtistSyncStatus.SHALLOW_ASSUMPTION
  val isAssumption: Boolean get() = syncStatus == ArtistSyncStatus.SYNC_ASSUMPTION || syncStatus == ArtistSyncStatus.SHALLOW_ASSUMPTION
}
