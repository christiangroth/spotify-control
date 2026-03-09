package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RecentlyPlayedFetchJobTests {

    private val playback: PlaybackPort = mockk(relaxed = true)

    private val job = RecentlyPlayedFetchJob(playback)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { playback.enqueueFetchRecentlyPlayed() }
    }
}
