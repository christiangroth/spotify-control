package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
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
    outboxPort: OutboxPort = mockk { every { getPartitionStats() } returns emptyList() },
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
    outboxPort = outboxPort,
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
  fun `startup partition summary queries outbox stats when paused notification enabled`() {
    val outboxPort: OutboxPort = mockk { every { getPartitionStats() } returns emptyList() }
    adapter(partitionPausedEnabled = true, outboxPort = outboxPort).onStartup(StartupEvent())
    verify { outboxPort.getPartitionStats() }
  }

  @Test
  fun `startup partition summary queries outbox stats when resumed notification enabled`() {
    val outboxPort: OutboxPort = mockk { every { getPartitionStats() } returns emptyList() }
    adapter(partitionResumedEnabled = true, outboxPort = outboxPort).onStartup(StartupEvent())
    verify { outboxPort.getPartitionStats() }
  }

  @Test
  fun `startup partition summary handles a mix of active and paused partitions with and without a known pause end`() {
    val outboxPort: OutboxPort = mockk {
      every { getPartitionStats() } returns listOf(
        OutboxPartitionStats(name = "to-spotify-catalog", status = "ACTIVE", documentCount = 0L, blockedUntil = null),
        OutboxPartitionStats(name = "to-spotify-playback", status = "PAUSED", documentCount = 0L, blockedUntil = Clock.System.now() + 2.hours),
        OutboxPartitionStats(name = "to-spotify-user", status = "PAUSED", documentCount = 0L, blockedUntil = null),
      )
    }
    adapter(partitionPausedEnabled = true, outboxPort = outboxPort).onStartup(StartupEvent())
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
  fun `partition paused notification before startup is suppressed`() {
    val outboxPort: OutboxPort = mockk()
    adapter(partitionPausedEnabled = true, outboxPort = outboxPort).onPartitionPaused("test-partition", "RATE_LIMITED")
    verify(exactly = 0) { outboxPort.getPartitionStats() }
  }

  @Test
  fun `partition paused notification after startup does not throw when no webhook url configured`() {
    val adapter = adapter(partitionPausedEnabled = true)
    adapter.onStartup(StartupEvent())
    adapter.onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification after startup includes status reason and pause end when known`() {
    val outboxPort: OutboxPort = mockk {
      every { getPartitionStats() } returns listOf(
        OutboxPartitionStats(name = "test-partition", status = "PAUSED", documentCount = 0L, blockedUntil = Clock.System.now() + 2.hours),
      )
    }
    val adapter = adapter(partitionPausedEnabled = true, outboxPort = outboxPort)
    adapter.onStartup(StartupEvent())
    adapter.onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification after startup handles blank reason and unknown pause end`() {
    val outboxPort: OutboxPort = mockk {
      every { getPartitionStats() } returns listOf(
        OutboxPartitionStats(name = "test-partition", status = "PAUSED", documentCount = 0L, blockedUntil = null),
      )
    }
    val adapter = adapter(partitionPausedEnabled = true, outboxPort = outboxPort)
    adapter.onStartup(StartupEvent())
    adapter.onPartitionPaused("test-partition", "unknown")
  }

  @Test
  fun `partition resumed notification does not throw when disabled`() {
    adapter().onPartitionActivated("test-partition")
  }

  @Test
  fun `partition resumed notification before startup is suppressed`() {
    adapter(partitionResumedEnabled = true).onPartitionActivated("test-partition")
  }

  @Test
  fun `partition resumed notification after startup does not throw when no webhook url configured`() {
    val adapter = adapter(partitionResumedEnabled = true)
    adapter.onStartup(StartupEvent())
    adapter.onPartitionActivated("test-partition")
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
