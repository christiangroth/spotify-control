package de.chrgroth.spotify.control.domain.port.out

import java.time.Instant

interface PlaybackActivityPort {
    fun isPlaybackActive(): Boolean
    fun lastActivityTimestamp(): Instant?
}
