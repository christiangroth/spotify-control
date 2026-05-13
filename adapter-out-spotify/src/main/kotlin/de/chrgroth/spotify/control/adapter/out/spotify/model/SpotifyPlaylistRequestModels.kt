package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyRemoveTrackObject(
  val uri: String,
)

@Serializable
internal data class SpotifyRemoveTrackAtPositionObject(
  val uri: String,
  val positions: List<Int>,
)

@Serializable
internal data class SpotifyRemovePlaylistTracksRequest(
  val items: List<SpotifyRemoveTrackObject>,
)

@Serializable
internal data class SpotifyRemovePlaylistTrackAtPositionRequest(
  val items: List<SpotifyRemoveTrackAtPositionObject>,
)

@Serializable
internal data class SpotifyAddPlaylistTracksRequest(
  val uris: List<String>,
  val position: Int? = null,
)
