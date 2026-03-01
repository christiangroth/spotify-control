package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify

@QuarkusTest
class RecentlyPlayedFetchJobTests {

    @InjectMock
    lateinit var recentlyPlayed: RecentlyPlayedPort

    @Inject
    lateinit var job: RecentlyPlayedFetchJob

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify(recentlyPlayed).enqueueUpdates()
    }
}
