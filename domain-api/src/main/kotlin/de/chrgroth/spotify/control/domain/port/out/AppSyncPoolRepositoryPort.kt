package de.chrgroth.spotify.control.domain.port.out

interface AppSyncPoolRepositoryPort {
    fun addArtists(artistIds: List<String>)
    fun addTracks(trackIds: List<String>)
    fun peekArtists(max: Int): List<String>
    fun peekTracks(max: Int): List<String>
    fun removeArtists(artistIds: List<String>)
    fun removeTracks(trackIds: List<String>)
}
