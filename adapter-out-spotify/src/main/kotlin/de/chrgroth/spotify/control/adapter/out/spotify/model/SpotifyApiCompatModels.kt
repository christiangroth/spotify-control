package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class CurrentlyPlayingContextObject(
  val item: JsonElement? = null,
  @SerialName("progress_ms") val progressMs: Long? = null,
  @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
internal data class PlaylistTrackObject(
  val item: JsonElement? = null,
)

@Serializable
internal data class PagingPlaylistTrackObject(
  val items: List<PlaylistTrackObject?> = emptyList(),
  val next: String? = null,
  @SerialName("snapshot_id") val snapshotId: String? = null,
)

@Serializable
internal data class AlbumObject(
  val id: String? = null,
  val name: String? = null,
  val images: List<ImageObject> = emptyList(),
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
  @SerialName("album_type") val albumType: String? = null,
  @SerialName("total_tracks") val totalTracks: Int? = null,
  val artists: List<SimplifiedArtistObject> = emptyList(),
  val tracks: PagingSimplifiedTrackObject? = null,
)

@Serializable
internal data class SimplifiedAlbumObject(
  val id: String? = null,
  val name: String? = null,
  val images: List<ImageObject> = emptyList(),
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
  @SerialName("album_type") val albumType: String? = null,
  @SerialName("total_tracks") val totalTracks: Int? = null,
  val artists: List<SimplifiedArtistObject> = emptyList(),
  @SerialName("external_urls") val externalUrls: ExternalUrlObject? = null,
)

@Serializable
internal data class PagingSimplifiedTrackObject(
  val items: List<SimplifiedTrackObject?> = emptyList(),
  val next: String? = null,
  val total: Int = 0,
)

@Serializable
internal data class ArtistDiscographyAlbumObject(
  val id: String? = null,
)

@Serializable
internal data class PagingArtistDiscographyAlbumObject(
  val items: List<ArtistDiscographyAlbumObject> = emptyList(),
  val next: String? = null,
)
