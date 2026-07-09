package de.chrgroth.spotify.control.domain.model.playback.aggregation

import kotlinx.datetime.LocalDate

data class PlaybackAggregation(
  val type: AggregationPeriodType,
  val periodStart: LocalDate,
  val totalPlaybackSeconds: Long,
  val eventCount: Long,
  val distinctArtistCount: Int,
  val distinctTrackCount: Int,
  val distinctAlbumCount: Int,
  val artistEntries: List<AggregationRankEntry>,
  val albumEntries: List<AggregationRankEntry>,
  val trackEntries: List<AggregationRankEntry>,
  val activityEntries: List<ActivityEntry>,
)
