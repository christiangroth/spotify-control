package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class CurrentlyPlayingFetchJobTests {

    private val playback: PlaybackPort = mockk(relaxed = true)
    private val scheduleState: CurrentlyPlayingScheduleState = CurrentlyPlayingScheduleState()

    private val job = CurrentlyPlayingFetchJob(playback, scheduleState)

    @Test
    fun `run calls enqueueUpdates when no playback active and interval elapsed`() {
        job.run()

        verify { playback.enqueueFetchCurrentlyPlaying() }
    }

    @Test
    fun `run skips enqueueUpdates when fast interval has not elapsed`() {
        job.run()
        scheduleState.onPlaybackDetected()

        job.run()

        verify(exactly = 1) { playback.enqueueFetchCurrentlyPlaying() }
    }

    @Test
    fun `run uses slow interval when no playback detected`() {
        job.run()
        val invocationsBefore = 1

        job.run()

        verify(exactly = invocationsBefore) { playback.enqueueFetchCurrentlyPlaying() }
    }

    @Test
    fun `schedule state reports playback active after detection`() {
        scheduleState.onPlaybackDetected()

        assertThat(scheduleState.isPlaybackActive()).isTrue()
    }

    @Test
    fun `schedule state reports playback inactive when no detection in slow interval`() {
        assertThat(scheduleState.isPlaybackActive()).isFalse()
    }

    @Test
    fun `schedule state reports playback inactive after slow interval passes`() {
        val pastTime = Instant.now().minus(CurrentlyPlayingScheduleState.SLOW_INTERVAL).minusSeconds(1)
        val state = CurrentlyPlayingScheduleState()
        state.lastPlaybackDetectedAt = pastTime

        assertThat(state.isPlaybackActive()).isFalse()
    }
}
