package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RecentlyPlayedFetchJobTests {

    private val recentlyPlayed: RecentlyPlayedPort = mockk(relaxed = true)

    private val job = RecentlyPlayedFetchJob(recentlyPlayed)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { recentlyPlayed.enqueueUpdates() }
    }
}
