package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify

@QuarkusTest
class RecentlyPlayedFetchJobTests {

    @InjectMock
    lateinit var outboxPort: OutboxPort

    @Inject
    lateinit var job: RecentlyPlayedFetchJob

    @Test
    fun `run enqueues FetchRecentlyPlayed event`() {
        job.run()

        verify(outboxPort).enqueue(AppOutboxEvent.FetchRecentlyPlayed)
    }
}
