package de.chrgroth.spotify.control.domain.port.out

import kotlin.time.Instant

interface PlaybackActivityPort {
    fun isPlaybackActive(): Boolean
    fun lastActivityTimestamp(): Instant?
}
