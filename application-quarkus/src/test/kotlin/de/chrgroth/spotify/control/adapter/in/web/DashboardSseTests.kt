package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.adapter.`in`.web.DashboardSseAdapter
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
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
class DashboardSseTests {

    @Inject
    lateinit var dashboardSseService: DashboardSseAdapter

    @Inject
    lateinit var dashboardRefreshPort: DashboardRefreshPort

    @Test
    fun `sse endpoint delivers refresh-playback-data event when user is notified`() {
        val userId = UserId("test-user-sse")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = dashboardSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        // Mock a change: notify the user via the refresh port
        dashboardRefreshPort.notifyUserPlaybackData(userId)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh-playback-data"), received.toList())

        cancellable.cancel()
    }

    @Test
    fun `sse endpoint delivers refresh-playlist-metadata event when playlist metadata is notified`() {
        val userId = UserId("test-user-sse-playlist")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = dashboardSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        dashboardRefreshPort.notifyUserPlaylistMetadata(userId)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh-playlist-metadata"), received.toList())

        cancellable.cancel()
    }

    @Test
    fun `sse endpoint delivers refresh-catalog-stats event to all connected users when catalog is notified`() {
        val userId1 = UserId("test-user-catalog-1")
        val userId2 = UserId("test-user-catalog-2")
        val received1 = CopyOnWriteArrayList<String>()
        val received2 = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)

        val cancellable1: Cancellable = dashboardSseService.stream(userId1)
            .subscribe().with(
                { event: String -> received1.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )
        val cancellable2: Cancellable = dashboardSseService.stream(userId2)
            .subscribe().with(
                { event: String -> received2.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        dashboardRefreshPort.notifyCatalogStats()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE catalog refresh events should be received by all users within 5 seconds")
        assertEquals(listOf("refresh-catalog-stats"), received1.toList())
        assertEquals(listOf("refresh-catalog-stats"), received2.toList())

        cancellable1.cancel()
        cancellable2.cancel()
    }
}
