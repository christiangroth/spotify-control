package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.RawPlaybackEvent
import de.chrgroth.spotify.control.domain.model.UserId
import kotlin.time.Instant

interface PlaybackEventViewerRepositoryPort {
    fun findRecentlyPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
    fun findRecentlyPartialPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
    fun findCurrentlyPlaying(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
}
