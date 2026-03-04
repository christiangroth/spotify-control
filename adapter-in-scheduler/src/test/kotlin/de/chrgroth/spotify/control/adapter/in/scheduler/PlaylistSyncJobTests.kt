package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.PlaylistSyncPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlaylistSyncJobTests {

    private val playlistSync: PlaylistSyncPort = mockk(relaxed = true)

    private val job = PlaylistSyncJob(playlistSync)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { playlistSync.enqueueUpdates() }
    }
}
