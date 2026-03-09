package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlaylistSyncJobTests {

    private val playlist: PlaylistPort = mockk(relaxed = true)

    private val job = PlaylistSyncJob(playlist)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { playlist.enqueueUpdates() }
    }
}
