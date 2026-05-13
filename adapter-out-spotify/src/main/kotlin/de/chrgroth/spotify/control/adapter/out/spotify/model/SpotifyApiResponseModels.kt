package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CurrentlyPlayingContextObject(
  val item: JsonElement? = null,
  @SerialName("progress_ms") val progressMs: Long? = null,
  @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
data class PlaylistTrackObject(
  val item: JsonElement? = null,
)

@Serializable
data class PagingPlaylistTrackObject(
  val items: List<PlaylistTrackObject?> = emptyList(),
  val next: String? = null,
  @SerialName("snapshot_id") val snapshotId: String? = null,
)

/**
 * Lenient replacement for the generated SimplifiedAlbumObject.
 * All fields are optional so that partial API responses don't cause MissingFieldException.
 */
@Serializable
data class SimplifiedAlbumObject(
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

/**
 * Lenient replacement for the generated ArtistDiscographyAlbumObject.
 * All fields are optional so that partial API responses don't cause MissingFieldException.
 * Extends SimplifiedAlbumObject with album_group field.
 */
@Serializable
data class ArtistDiscographyAlbumObject(
  val id: String? = null,
  val name: String? = null,
  val images: List<ImageObject> = emptyList(),
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
  @SerialName("album_type") val albumType: String? = null,
  @SerialName("total_tracks") val totalTracks: Int? = null,
  val artists: List<SimplifiedArtistObject> = emptyList(),
  @SerialName("external_urls") val externalUrls: ExternalUrlObject? = null,
  @SerialName("available_markets") val availableMarkets: List<String> = emptyList(),
  @SerialName("album_group") val albumGroup: String? = null,
)

/**
 * Lenient replacement for the generated PagingArtistDiscographyAlbumObject.
 * All fields are optional so that partial API responses don't cause MissingFieldException.
 */
@Serializable
data class PagingArtistDiscographyAlbumObject(
  val items: List<ArtistDiscographyAlbumObject?> = emptyList(),
  val next: String? = null,
  val offset: Int? = null,
  val limit: Int? = null,
  val total: Int? = null,
  val href: String? = null,
  val previous: String? = null,
)
