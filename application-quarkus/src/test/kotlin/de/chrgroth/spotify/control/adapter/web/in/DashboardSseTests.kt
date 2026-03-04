package de.chrgroth.spotify.control.adapter.web.`in`

import de.chrgroth.spotify.control.adapter.`in`.web.ui.DashboardSseService
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
    lateinit var dashboardSseService: DashboardSseService

    @Inject
    lateinit var dashboardRefreshPort: DashboardRefreshPort

    @Test
    fun `sse endpoint delivers refresh event when user is notified`() {
        val userId = UserId("test-user-sse")
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val cancellable: Cancellable = dashboardSseService.stream(userId)
            .subscribe().with(
                { event: String -> received.add(event); latch.countDown() },
                { _: Throwable -> /* ignore errors */ },
            )

        // Mock a change: notify the user via the refresh port
        dashboardRefreshPort.notifyUser(userId)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
        assertEquals(listOf("refresh"), received.toList())

        cancellable.cancel()
    }
}
