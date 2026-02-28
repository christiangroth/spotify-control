package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxProcessor
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxTaskPriority
import de.chrgroth.spotify.control.util.outbox.OutboxTaskStatus
import de.chrgroth.spotify.control.util.outbox.OutboxWakeupService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class OutboxPartitionWorkerDispatchTests {

    private val outboxProcessor: OutboxProcessor = mockk()
    private val wakeupService: OutboxWakeupService = mockk(relaxed = true)
    private val handlerPort: OutboxHandlerPort = mockk()

    private val worker = OutboxPartitionWorker(outboxProcessor, wakeupService, handlerPort)

    private val userId = "user-123"

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
    fun `FetchRecentlyPlayedForUser dispatches to handleFetchRecentlyPlayedForUser`() {
        val task = buildTask(AppOutboxEvent.FetchRecentlyPlayedForUser.KEY, userId)
        every { handlerPort.handleFetchRecentlyPlayedForUser(UserId(userId)) } returns Unit.right()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { handlerPort.handleFetchRecentlyPlayedForUser(UserId(userId)) }
    }

    @Test
    fun `UpdateUserProfileForUser dispatches to handleUpdateUserProfileForUser`() {
        val task = buildTask(AppOutboxEvent.UpdateUserProfileForUser.KEY, userId)
        every { handlerPort.handleUpdateUserProfileForUser(UserId(userId)) } returns Unit.right()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Right::class.java)
        verify { handlerPort.handleUpdateUserProfileForUser(UserId(userId)) }
    }

    @Test
    fun `unknown event type returns OutboxError left`() {
        val task = buildTask("UnknownEvent", "payload")

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `handler failure propagates as left`() {
        val task = buildTask(AppOutboxEvent.FetchRecentlyPlayedForUser.KEY, userId)
        val error = OutboxError("fetch failed")
        every { handlerPort.handleFetchRecentlyPlayedForUser(UserId(userId)) } returns error.left()

        val result = worker.dispatch(task)

        assertThat(result).isInstanceOf(Either.Left::class.java)
    }
}
