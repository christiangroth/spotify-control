package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlinx.datetime.LocalDate

class SlackNotificationAdapterTests {

  private fun adapter(
    webhookUrl: Optional<String> = Optional.empty(),
    username: String = "SpCtl",
    iconEmoji: String = ":robot_face:",
    startupEnabled: Boolean = false,
    stoppingEnabled: Boolean = false,
    partitionPausedEnabled: Boolean = false,
    partitionResumedEnabled: Boolean = false,
    checkPassedEnabled: Boolean = false,
    violationsChangedEnabled: Boolean = false,
    taskFailedEnabled: Boolean = false,
    tokenRefreshFailedEnabled: Boolean = false,
    syncFailedEnabled: Boolean = false,
    weeklyDigestEnabled: Boolean = false,
  ) = SlackNotificationAdapter(
    version = "1.0.0-TEST",
    webhookUrl = webhookUrl,
    username = username,
    iconEmoji = iconEmoji,
    startupEnabled = startupEnabled,
    stoppingEnabled = stoppingEnabled,
    partitionPausedEnabled = partitionPausedEnabled,
    partitionResumedEnabled = partitionResumedEnabled,
    checkPassedEnabled = checkPassedEnabled,
    violationsChangedEnabled = violationsChangedEnabled,
    taskFailedEnabled = taskFailedEnabled,
    tokenRefreshFailedEnabled = tokenRefreshFailedEnabled,
    syncFailedEnabled = syncFailedEnabled,
    weeklyDigestEnabled = weeklyDigestEnabled,
  )

  @Test
  fun `adapter logs on construction when webhook url is blank`() {
    adapter(webhookUrl = Optional.empty())
  }

  @Test
  fun `adapter logs on construction when webhook url is set`() {
    adapter(webhookUrl = Optional.of("https://hooks.slack.com/test"))
  }

  @Test
  fun `startup notification does not throw when disabled`() {
    adapter().onStartup(StartupEvent())
  }

  @Test
  fun `startup notification does not throw when no webhook url configured`() {
    adapter(startupEnabled = true).onStartup(StartupEvent())
  }

  @Test
  fun `stopping notification does not throw when disabled`() {
    adapter().onShutdown(ShutdownEvent())
  }

  @Test
  fun `stopping notification does not throw when no webhook url configured`() {
    adapter(stoppingEnabled = true).onShutdown(ShutdownEvent())
  }

  @Test
  fun `partition paused notification does not throw when disabled`() {
    adapter().onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification does not throw when no webhook url configured`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification includes status reason`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification handles blank reason`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "unknown")
  }

  @Test
  fun `partition resumed notification does not throw when disabled`() {
    adapter().onPartitionActivated("test-partition")
  }

  @Test
  fun `partition resumed notification does not throw when no webhook url configured`() {
    adapter(partitionResumedEnabled = true).onPartitionActivated("test-partition")
  }

  @Test
  fun `check passed notification does not throw when disabled`() {
    adapter().notifyCheckPassed(buildCheck(), "My Playlist")
  }

  @Test
  fun `check passed notification does not throw when no webhook url configured`() {
    adapter(checkPassedEnabled = true).notifyCheckPassed(buildCheck(), "My Playlist")
  }

  @Test
  fun `check passed notification falls back to playlist id when name is unknown`() {
    adapter(checkPassedEnabled = true).notifyCheckPassed(buildCheck(), null)
  }

  @Test
  fun `violations changed notification does not throw when disabled`() {
    adapter().notifyViolationsChanged(
      buildCheck(violations = listOf(de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation("t1", "Artist – Track"))),
      "My Playlist",
      previousViolationCount = 1,
    )
  }

  @Test
  fun `violations changed notification does not throw when no webhook url configured`() {
    adapter(violationsChangedEnabled = true).notifyViolationsChanged(
      buildCheck(violations = listOf(de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation("t1", "Artist – Track"))),
      "My Playlist",
      previousViolationCount = 1,
    )
  }

  @Test
  fun `violations changed notification does not throw for first check with no previous count`() {
    adapter(violationsChangedEnabled = true).notifyViolationsChanged(
      buildCheck(violations = listOf(de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation("t1", "Artist – Track"))),
      "My Playlist",
      previousViolationCount = null,
    )
  }

  @Test
  fun `task failed notification does not throw when disabled`() {
    adapter().onTaskFailed("to-spotify-playlist", "SyncPlaylistInfo")
  }

  @Test
  fun `task failed notification does not throw when no webhook url configured`() {
    adapter(taskFailedEnabled = true).onTaskFailed("to-spotify-playlist", "SyncPlaylistInfo")
  }

  @Test
  fun `token refresh failed notification does not throw when disabled`() {
    adapter().notifyTokenRefreshFailed()
  }

  @Test
  fun `token refresh failed notification does not throw when no webhook url configured`() {
    adapter(tokenRefreshFailedEnabled = true).notifyTokenRefreshFailed()
  }

  @Test
  fun `sync failed notification does not throw when disabled`() {
    adapter().notifySyncFailed("PLAYLIST-001")
  }

  @Test
  fun `sync failed notification does not throw when no webhook url configured`() {
    adapter(syncFailedEnabled = true).notifySyncFailed("PLAYLIST-001")
  }

  @Test
  fun `weekly digest notification does not throw when disabled`() {
    adapter().notifyWeeklyDigest(buildAggregation())
  }

  @Test
  fun `weekly digest notification does not throw when no webhook url configured`() {
    adapter(weeklyDigestEnabled = true).notifyWeeklyDigest(buildAggregation())
  }

  @Test
  fun `weekly digest notification does not throw when aggregation is empty`() {
    adapter(weeklyDigestEnabled = true).notifyWeeklyDigest(buildAggregation(eventCount = 0L, trackEntries = emptyList(), artistEntries = emptyList()))
  }

  private fun buildCheck(
    playlistId: String = "playlist-1",
    checkId: String = "playlist-1:duplicate-tracks",
    succeeded: Boolean = true,
    violations: List<de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation> = emptyList(),
  ) = de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck(
    checkId = checkId,
    playlistId = de.chrgroth.spotify.control.domain.model.playlist.PlaylistId(playlistId),
    lastCheck = kotlin.time.Clock.System.now(),
    succeeded = succeeded,
    violations = violations,
  )

  private fun buildAggregation(
    eventCount: Long = 5L,
    trackEntries: List<AggregationRankEntry> = listOf(AggregationRankEntry("track-1", "Top Track", 300L)),
    artistEntries: List<AggregationRankEntry> = listOf(AggregationRankEntry("artist-1", "Top Artist", 300L)),
  ) = PlaybackAggregation(
    type = AggregationPeriodType.WEEK,
    periodStart = LocalDate(2024, 1, 15),
    totalPlaybackSeconds = 3600L,
    eventCount = eventCount,
    distinctArtistCount = artistEntries.size,
    distinctTrackCount = trackEntries.size,
    distinctAlbumCount = 0,
    artistEntries = artistEntries,
    albumEntries = emptyList(),
    trackEntries = trackEntries,
    activityEntries = emptyList(),
  )
}
