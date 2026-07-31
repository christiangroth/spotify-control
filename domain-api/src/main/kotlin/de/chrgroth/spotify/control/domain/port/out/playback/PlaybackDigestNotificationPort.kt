package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation

interface PlaybackDigestNotificationPort {
  fun notifyWeeklyDigest(aggregation: PlaybackAggregation)
}
