package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CurrentlyPlayingFetchJobTests {

    private val playback: PlaybackPort = mockk(relaxed = true)
    private val job = CurrentlyPlayingFetchJob(playback)

    @Test
    fun `run calls enqueueFetchCurrentlyPlaying`() {
        job.run()

        verify { playback.enqueueFetchCurrentlyPlaying() }
    }
}
