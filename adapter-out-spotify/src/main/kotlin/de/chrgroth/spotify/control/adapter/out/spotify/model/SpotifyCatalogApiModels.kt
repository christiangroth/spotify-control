package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyArtistResponse(
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
    val type: String? = null,
)

@Serializable
internal data class SpotifyAlbumRef(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    @SerialName("total_tracks") val totalTracks: Int? = null,
    val artists: List<SpotifyArtistRef> = emptyList(),
)

@Serializable
internal data class SpotifyTrackResponse(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("is_local") val isLocal: Boolean = false,
    val artists: List<SpotifyArtistRef> = emptyList(),
    val album: SpotifyAlbumRef? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
)
