package de.chrgroth.spotify.control.domain.model.playlist

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId

data class PlaylistTrack(
  val trackId: TrackId,
  val artistIds: List<ArtistId>,
  val albumId: AlbumId?,
  // Denormalized copy of artistIds.firstOrNull(), persisted per track so reads (e.g. findDistinctArtistIds)
  // don't need to recompute it via an aggregation over every track on every request.
  val mainArtistId: ArtistId? = artistIds.firstOrNull(),
)
