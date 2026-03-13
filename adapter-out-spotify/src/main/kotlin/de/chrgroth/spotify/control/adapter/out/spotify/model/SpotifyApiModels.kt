package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shared structures

@Serializable
internal data class SpotifyImage(val url: String)

@Serializable
internal data class SpotifyArtistRef(val id: String, val name: String)

// Auth

@Serializable
internal data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
internal data class SpotifyUserProfileResponse(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

// Catalog

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

// Playback

@Serializable
internal data class SpotifyCurrentlyPlayingResponse(
    val item: SpotifyTrackResponse? = null,
    @SerialName("progress_ms") val progressMs: Long? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
internal data class SpotifyPlayHistoryObject(
    val track: SpotifyTrackResponse,
    @SerialName("played_at") val playedAt: String,
)

@Serializable
internal data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyPlayHistoryObject> = emptyList(),
    val next: String? = null,
)

// Playlists

@Serializable
internal data class SpotifyOwner(val id: String)

@Serializable
internal data class SpotifyPlaylistResponse(
    val id: String,
    val name: String,
    @SerialName("snapshot_id") val snapshotId: String,
    val owner: SpotifyOwner,
)

@Serializable
internal data class SpotifyUserPlaylistsResponse(
    val items: List<SpotifyPlaylistResponse> = emptyList(),
    val next: String? = null,
)

@Serializable
internal data class SpotifyPlaylistTrackObject(
    val track: SpotifyTrackResponse? = null,
)

@Serializable
internal data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackObject> = emptyList(),
    val next: String? = null,
    @SerialName("snapshot_id") val snapshotId: String? = null,
)
