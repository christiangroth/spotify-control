package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.util.outbox.OutboxTaskStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainOutboxTaskDispatcherTests {

    private val handlerPort: OutboxHandlerPort = mockk()

    private val subject = DomainOutboxTaskDispatcher(handlerPort)

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

        val result = subject.dispatch(task)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { handlerPort.handle(event) }
    }

    @Test
    fun `UpdateUserProfile dispatches to handle(UpdateUserProfile)`() {
        val event = DomainOutboxEvent.UpdateUserProfile(userIdObj)
        val task = buildTask(DomainOutboxEvent.UpdateUserProfile.KEY, userId)
        every { handlerPort.handle(event) } returns Unit.right()

        val result = subject.dispatch(task)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { handlerPort.handle(event) }
    }

    @Test
    fun `unknown event type returns Failed result`() {
        val task = buildTask("UnknownEvent", "payload")

        val result = subject.dispatch(task)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `handler failure propagates as Failed result`() {
        val event = DomainOutboxEvent.FetchRecentlyPlayed(userIdObj)
        val task = buildTask(DomainOutboxEvent.FetchRecentlyPlayed.KEY, userId)
        val cause = RuntimeException("root cause")
        val error = OutboxError("fetch failed", cause)
        every { handlerPort.handle(event) } returns error.left()

        val result = subject.dispatch(task)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).isEqualTo("fetch failed")
        assertThat(result.cause).isSameAs(cause)
    }
}
