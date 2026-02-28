package de.chrgroth.spotify.control.util.outbox

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class OutboxProcessorTests {

    private val repository: OutboxRepository = mockk()
    private val retryPolicy = RetryPolicy(
        maxAttempts = 3,
        backoff = listOf(Duration.ofSeconds(5), Duration.ofSeconds(10)),
    )
    private val processor = OutboxProcessor(repository, retryPolicy)

    private val partition = object : OutboxPartition {
        override val key = "test-partition"
    }

    private fun task(attempts: Int = 0) = OutboxTask(
        id = "task-1",
        partition = partition.key,
        eventType = "TEST_EVENT",
        payload = """{"foo":"bar"}""",
        deduplicationKey = "dedup-1",
        status = OutboxTaskStatus.PROCESSING,
        attempts = attempts,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        nextRetryAt = null,
        priority = OutboxTaskPriority.NORMAL,
        lastError = null,
    )

    @Test
    fun `processNext returns false when no task is available`() {
        every { repository.claim(partition) } returns null

        val result = processor.processNext(partition) { Unit.right() }

        assertThat(result).isFalse()
    }

    @Test
    fun `processNext calls complete on successful dispatch`() {
        val task = task()
        every { repository.claim(partition) } returns task
        every { repository.complete(task) } just runs

        val result = processor.processNext(partition) { Unit.right() }

        assertThat(result).isTrue()
        verify { repository.complete(task) }
        verify(exactly = 0) { repository.fail(any(), any(), any()) }
    }

    @Test
    fun `processNext calls fail with retry time when dispatch fails and attempts below maxAttempts`() {
        val task = task(attempts = 0)
        every { repository.claim(partition) } returns task
        val capturedNextRetryAt = mutableListOf<Instant?>()
        every { repository.fail(task, any(), captureNullable(capturedNextRetryAt)) } just runs

        val result = processor.processNext(partition) { OutboxError("dispatch failed").left() }

        assertThat(result).isTrue()
        assertThat(capturedNextRetryAt.first()).isNotNull()
    }

    @Test
    fun `processNext calls fail with null nextRetryAt when attempts reach maxAttempts`() {
        val task = task(attempts = 2) // maxAttempts=3, so newAttempts=3 >= maxAttempts
        every { repository.claim(partition) } returns task
        every { repository.fail(task, any(), null) } just runs

        val result = processor.processNext(partition) { OutboxError("permanent failure").left() }

        assertThat(result).isTrue()
        verify { repository.fail(task, "permanent failure", null) }
    }

    @Test
    fun `processNext uses backoff list correctly for retry delays`() {
        val task = task(attempts = 1) // second attempt -> use backoff[1] = 10s
        every { repository.claim(partition) } returns task
        val capturedNextRetryAt = mutableListOf<Instant?>()
        every { repository.fail(task, any(), captureNullable(capturedNextRetryAt)) } just runs

        val before = Instant.now()
        processor.processNext(partition) { OutboxError("fail").left() }
        val after = Instant.now()

        // nextRetryAt should be approximately now + 10 seconds
        val captured = capturedNextRetryAt.first()!!
        assertThat(captured).isAfter(before.plusSeconds(9))
        assertThat(captured).isBefore(after.plusSeconds(11))
    }

    @Test
    fun `processNext uses last backoff entry when attempts exceed backoff list`() {
        val task = task(attempts = 5) // beyond backoff list size
        val largeRetryPolicy = RetryPolicy(
            maxAttempts = 10,
            backoff = listOf(Duration.ofSeconds(5), Duration.ofSeconds(10)),
        )
        val processorWithLargePolicy = OutboxProcessor(repository, largeRetryPolicy)
        every { repository.claim(partition) } returns task
        val capturedNextRetryAt = mutableListOf<Instant?>()
        every { repository.fail(task, any(), captureNullable(capturedNextRetryAt)) } just runs

        val before = Instant.now()
        processorWithLargePolicy.processNext(partition) { OutboxError("fail").left() }

        // Should use last backoff entry (10s) for out-of-bounds index
        val captured = capturedNextRetryAt.first()!!
        assertThat(captured).isAfter(before.plusSeconds(9))
    }

    @Test
    fun `processNext returns true when task was claimed even on dispatch failure`() {
        val task = task()
        every { repository.claim(partition) } returns task
        every { repository.fail(task, any(), any()) } just runs

        val result = processor.processNext(partition) { OutboxError("error").left() }

        assertThat(result).isTrue()
    }
}
