package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.outbox.domain.port.`in`.ArchiverPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ArchiveFailedOutboxTasksStarterTests {

    private val archiverPort: ArchiverPort = mockk()
    private val starter = ArchiveFailedOutboxTasksStarter(archiverPort, 30L)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("ArchiveFailedOutboxTasksStarter-v1")
    }

    @Test
    fun `execute deletes old archive entries and logs result`() {
        every { archiverPort.deleteOlderThan(any()) } returns 3L

        starter.execute()

        verify(exactly = 1) { archiverPort.deleteOlderThan(match { it.isBefore(Instant.now()) }) }
    }

    @Test
    fun `execute handles zero deleted entries without throwing`() {
        every { archiverPort.deleteOlderThan(any()) } returns 0L

        starter.execute()

        verify(exactly = 1) { archiverPort.deleteOlderThan(any()) }
    }
}
