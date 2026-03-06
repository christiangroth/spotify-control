package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CurrentlyPlayingFetchJobTests {

    private val currentlyPlaying: CurrentlyPlayingPort = mockk(relaxed = true)

    private val job = CurrentlyPlayingFetchJob(currentlyPlaying)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { currentlyPlaying.enqueueUpdates() }
    }
}
