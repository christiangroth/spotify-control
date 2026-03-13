package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyImage(val url: String)

@Serializable
internal data class SpotifyArtistRef(val id: String, val name: String)
