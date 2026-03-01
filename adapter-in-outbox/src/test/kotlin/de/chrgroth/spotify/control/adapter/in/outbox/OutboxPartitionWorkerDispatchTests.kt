package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.Outbox
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxProcessor
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority
import de.chrgroth.spotify.control.util.outbox.OutboxTaskStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class OutboxPartitionWorkerDispatchTests {

    private val outboxProcessor: OutboxProcessor = mockk()
    private val outbox: Outbox = mockk(relaxed = true)
    private val handlerPort: OutboxHandlerPort = mockk()

    private val worker = OutboxPartitionWorker(outboxProcessor, outbox, handlerPort)

    private val userId = "user-123"
    private val userIdObj = UserId(userId)

    private fun buildTask(eventType: String, payload: String) = OutboxTask(
        id = "task-id",
        partition = "test-partition",
        eventType = eventType,
        payload = payload,
        deduplicationKey = "$eventType:$payload",
        status = OutboxTaskStatus.PROCESSING,
        attempts = 0,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        nextRetryAt = null,
        priority = OutboxTaskPriority.NORMAL,
        lastError = null,
    )

    @Test
    fun `FetchRecentlyPlayed dispatches to handle(FetchRecentlyPlayed)`() {
        val event = DomainOutboxEvent.FetchRecentlyPlayed(userIdObj)
        val task = buildTask(DomainOutboxEvent.FetchRecentlyPlayed.KEY, userId)
        every { handlerPort.handle(event) } returns Unit.right()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { handlerPort.handle(event) }
    }

    @Test
    fun `UpdateUserProfile dispatches to handle(UpdateUserProfile)`() {
        val event = DomainOutboxEvent.UpdateUserProfile(userIdObj)
        val task = buildTask(DomainOutboxEvent.UpdateUserProfile.KEY, userId)
        every { handlerPort.handle(event) } returns Unit.right()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { handlerPort.handle(event) }
    }

    @Test
    fun `unknown event type returns OutboxError left`() {
        val task = buildTask("UnknownEvent", "payload")

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `handler failure propagates as left`() {
        val event = DomainOutboxEvent.FetchRecentlyPlayed(userIdObj)
        val task = buildTask(DomainOutboxEvent.FetchRecentlyPlayed.KEY, userId)
        val error = OutboxError("fetch failed")
        every { handlerPort.handle(event) } returns error.left()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }
}
