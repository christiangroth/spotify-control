package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.StarterSkipPredicate
import de.chrgroth.spotify.control.adapter.out.scheduler.CurrentlyPlayingScheduleState
import io.mockk.every
import io.mockk.mockk
import io.quarkus.scheduler.ScheduledExecution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class CurrentlyPlayingSkipPredicateTests {

    private val scheduleState: CurrentlyPlayingScheduleState = CurrentlyPlayingScheduleState()
    private val starterSkipPredicate: StarterSkipPredicate = mockk(relaxed = true)
    private val execution: ScheduledExecution = mockk(relaxed = true)

    private val predicate = CurrentlyPlayingSkipPredicate(scheduleState, starterSkipPredicate)

    @Test
    fun `skips when StarterSkipPredicate returns true`() {
        every { starterSkipPredicate.test(execution) } returns true

        assertThat(predicate.test(execution)).isTrue()
    }

    @Test
    fun `does not skip on first call when no playback active`() {
        every { starterSkipPredicate.test(execution) } returns false

        assertThat(predicate.test(execution)).isFalse()
    }

    @Test
    fun `skips on second immediate call when no playback active (slow interval not elapsed)`() {
        every { starterSkipPredicate.test(execution) } returns false
        predicate.test(execution)

        assertThat(predicate.test(execution)).isTrue()
    }

    @Test
    fun `skips on second immediate call when playback active (fast interval not elapsed)`() {
        every { starterSkipPredicate.test(execution) } returns false
        scheduleState.onPlaybackDetected()
        predicate.test(execution)

        assertThat(predicate.test(execution)).isTrue()
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
        val state = CurrentlyPlayingScheduleState(pastTime)

        assertThat(state.isPlaybackActive()).isFalse()
    }
}
