package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.util.outbox.Outbox
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArchiveFailedOutboxTasksStarterTests {

    private val outbox: Outbox = mockk()
    private val starter = ArchiveFailedOutboxTasksStarter(outbox)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("ArchiveFailedOutboxTasksStarter-v1")
    }

    @Test
    fun `execute archives failed tasks and logs result`() {
        every { outbox.archiveFailedTasks() } returns 3L

        starter.execute()

        verify(exactly = 1) { outbox.archiveFailedTasks() }
    }

    @Test
    fun `execute handles zero archived tasks without throwing`() {
        every { outbox.archiveFailedTasks() } returns 0L

        starter.execute()

        verify(exactly = 1) { outbox.archiveFailedTasks() }
    }
}
