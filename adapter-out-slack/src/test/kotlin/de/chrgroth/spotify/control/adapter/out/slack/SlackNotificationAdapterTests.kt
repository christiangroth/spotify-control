package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.outbox.OutboxPartition
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test

class SlackNotificationAdapterTests {

    private val testPartition = object : OutboxPartition {
        override val key = "test-partition"
        override val pauseOnRateLimit = true
    }

    private fun adapter(
        webhookUrl: String = "",
        startupEnabled: Boolean = false,
        stoppingEnabled: Boolean = false,
        partitionPausedEnabled: Boolean = false,
        partitionResumedEnabled: Boolean = false,
    ) = SlackNotificationAdapter(
        version = "1.0.0-TEST",
        webhookUrl = webhookUrl,
        startupEnabled = startupEnabled,
        stoppingEnabled = stoppingEnabled,
        partitionPausedEnabled = partitionPausedEnabled,
        partitionResumedEnabled = partitionResumedEnabled,
    )

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
}
