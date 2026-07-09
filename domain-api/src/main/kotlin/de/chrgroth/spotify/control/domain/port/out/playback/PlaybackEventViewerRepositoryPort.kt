package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.RawPlaybackEvent
import kotlin.time.Instant

interface PlaybackEventViewerRepositoryPort {
  fun findRecentlyPlayed(from: Instant, to: Instant): List<RawPlaybackEvent>
  fun findRecentlyPartialPlayed(from: Instant, to: Instant): List<RawPlaybackEvent>
  fun findCurrentlyPlaying(from: Instant, to: Instant): List<RawPlaybackEvent>
}
