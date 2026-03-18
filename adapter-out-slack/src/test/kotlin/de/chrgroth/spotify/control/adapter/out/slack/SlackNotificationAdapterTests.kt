package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.outbox.OutboxPartition
import de.chrgroth.outbox.OutboxPartitionInfo
import de.chrgroth.outbox.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.util.Optional

class SlackNotificationAdapterTests {

    private val repository: OutboxRepository = mockk()

    private val testPartition = object : OutboxPartition {
        override val key = "test-partition"
        override val pauseOnRateLimit = true
    }

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
    ) = SlackNotificationAdapter(
        repository = repository,
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
        adapter().onPartitionPaused(testPartition)
    }

    @Test
    fun `partition paused notification does not throw when no webhook url configured`() {
        every { repository.findPartition(testPartition) } returns partitionInfo("RATE_LIMITED")
        adapter(partitionPausedEnabled = true).onPartitionPaused(testPartition)
    }

    @Test
    fun `partition paused notification includes status reason`() {
        every { repository.findPartition(testPartition) } returns partitionInfo("RATE_LIMITED")
        adapter(partitionPausedEnabled = true).onPartitionPaused(testPartition)
    }

    @Test
    fun `partition paused notification handles null status reason`() {
        every { repository.findPartition(testPartition) } returns null
        adapter(partitionPausedEnabled = true).onPartitionPaused(testPartition)
    }

    @Test
    fun `partition resumed notification does not throw when disabled`() {
        adapter().onPartitionActivated(testPartition)
    }

    @Test
    fun `partition resumed notification does not throw when no webhook url configured`() {
        adapter(partitionResumedEnabled = true).onPartitionActivated(testPartition)
    }

    @Test
    fun `check passed notification does not throw when disabled`() {
        adapter().notifyCheckPassed(buildCheck())
    }

    @Test
    fun `check passed notification does not throw when no webhook url configured`() {
        adapter(checkPassedEnabled = true).notifyCheckPassed(buildCheck())
    }

    @Test
    fun `violations changed notification does not throw when disabled`() {
        adapter().notifyViolationsChanged(buildCheck(violations = listOf("Artist – Track")))
    }

    @Test
    fun `violations changed notification does not throw when no webhook url configured`() {
        adapter(violationsChangedEnabled = true).notifyViolationsChanged(buildCheck(violations = listOf("Artist – Track")))
    }

    private fun buildCheck(
        playlistId: String = "playlist-1",
        checkId: String = "playlist-1:duplicate-tracks",
        succeeded: Boolean = true,
        violations: List<String> = emptyList(),
    ) = de.chrgroth.spotify.control.domain.model.AppPlaylistCheck(
        checkId = checkId,
        playlistId = playlistId,
        lastCheck = kotlin.time.Clock.System.now(),
        succeeded = succeeded,
        violations = violations,
    )

    private fun partitionInfo(statusReason: String) =
        OutboxPartitionInfo(key = testPartition.key, status = "PAUSED", statusReason = statusReason, pausedUntil = null)
}
