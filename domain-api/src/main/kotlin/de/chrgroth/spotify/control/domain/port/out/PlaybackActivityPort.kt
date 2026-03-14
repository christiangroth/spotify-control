package de.chrgroth.spotify.control.domain.port.out

interface PlaybackActivityPort {
    fun isPlaybackActive(): Boolean
}
