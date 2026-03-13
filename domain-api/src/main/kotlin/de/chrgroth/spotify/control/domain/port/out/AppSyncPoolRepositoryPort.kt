package de.chrgroth.spotify.control.domain.port.out

interface AppSyncPoolRepositoryPort {
    fun addArtists(artistIds: List<String>)
    fun addTracks(trackIds: List<String>)
    fun popArtists(max: Int): List<String>
    fun popTracks(max: Int): List<String>
}
