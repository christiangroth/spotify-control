package de.chrgroth.spotify.control.adapter.web.`in`

import de.chrgroth.spotify.control.adapter.`in`.web.ui.HealthSseService
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.util.outbox.OutboxPartitionObserver
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.subscription.Cancellable
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
class HealthSseTests {

    @Inject
    lateinit var healthSseService: HealthSseService

    @Inject
    lateinit var outgoingRequestStatsObserver: OutgoingRequestStatsObserver

    @Inject
    lateinit var outboxPartitionObserver: OutboxPartitionObserver

    @Test
    fun `sse endpoint delivers refresh-outgoing-http-calls event when outgoing request is recorded`() {
        val userId = UserId("test-user-health-sse-http")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = healthSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        outgoingRequestStatsObserver.onRequestRecorded()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh-outgoing-http-calls"), received.toList())

        cancellable.cancel()
    }

    @Test
    fun `sse endpoint delivers refresh-outbox-partitions event when partition is activated`() {
        val userId = UserId("test-user-health-sse-outbox")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = healthSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        outboxPartitionObserver.onPartitionActivated(DomainOutboxPartition.ToSpotify)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh-outbox-partitions"), received.toList())

        cancellable.cancel()
    }

    @Test
    fun `sse endpoint delivers refresh-outbox-partitions event when task is enqueued`() {
        val userId = UserId("test-user-health-sse-outbox-enqueue")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = healthSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        outboxPartitionObserver.onTaskEnqueued(DomainOutboxPartition.ToSpotify)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh-outbox-partitions"), received.toList())

        cancellable.cancel()
    }
}
