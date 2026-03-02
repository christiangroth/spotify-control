package de.chrgroth.spotify.control.util.outbox

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.time.Instant

class OutboxStartupRecoveryTests {

    private val outbox: Outbox = mockk()
    private val dispatcher: OutboxTaskDispatcher = mockk()
    private val recovery = OutboxStartupRecovery(outbox, dispatcher)

    private val partition = object : OutboxPartition {
        override val key = "test-partition"
    }

    private val startupEvent = StartupEvent()

    @Test
    fun `onStart signals active partition normally`() {
        every { outbox.resetStaleProcessingTasks() } just runs
        every { dispatcher.partitions } returns listOf(partition)
        every { outbox.findPartition(partition) } returns null
        every { outbox.signal(partition) } just runs

        recovery.onStart(startupEvent)

        verify { outbox.resetStaleProcessingTasks() }
        verify { outbox.signal(partition) }
    }

    @Test
    fun `onStart reactivates expired paused partition`() {
        val pausedUntil = Instant.now().minusSeconds(60)
        val partitionInfo = OutboxPartitionInfo(
            key = partition.key,
            status = OutboxPartitionStatus.PAUSED.name,
            statusReason = "rate_limited",
            pausedUntil = pausedUntil,
        )

        every { outbox.resetStaleProcessingTasks() } just runs
        every { dispatcher.partitions } returns listOf(partition)
        every { outbox.findPartition(partition) } returns partitionInfo
        every { outbox.activatePartition(partition) } just runs
        every { outbox.signal(partition) } just runs

        recovery.onStart(startupEvent)

        verify { outbox.activatePartition(partition) }
        verify { outbox.signal(partition) }
    }

    @Test
    fun `onStart does not crash when resetStaleProcessingTasks throws`() {
        every { outbox.resetStaleProcessingTasks() } throws RuntimeException("MongoDB auth failed")
        every { dispatcher.partitions } returns listOf(partition)
        every { outbox.findPartition(partition) } returns null
        every { outbox.signal(partition) } just runs

        recovery.onStart(startupEvent)

        verify { outbox.signal(partition) }
    }

    @Test
    fun `onStart signals partition and does not crash when findPartition throws`() {
        every { outbox.resetStaleProcessingTasks() } just runs
        every { dispatcher.partitions } returns listOf(partition)
        every { outbox.findPartition(partition) } throws RuntimeException("MongoDB auth failed")
        every { outbox.signal(partition) } just runs

        recovery.onStart(startupEvent)

        verify { outbox.signal(partition) }
    }

    @Test
    fun `onStart handles multiple partitions where one fails`() {
        val partitionB = object : OutboxPartition {
            override val key = "partition-b"
        }

        every { outbox.resetStaleProcessingTasks() } just runs
        every { dispatcher.partitions } returns listOf(partition, partitionB)
        every { outbox.findPartition(partition) } throws RuntimeException("MongoDB auth failed")
        every { outbox.findPartition(partitionB) } returns null
        every { outbox.signal(partition) } just runs
        every { outbox.signal(partitionB) } just runs

        recovery.onStart(startupEvent)

        verify { outbox.signal(partition) }
        verify { outbox.signal(partitionB) }
    }
}
