package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class UserProfileUpdateJobTests {

    private val userProfile: UserProfilePort = mockk(relaxed = true)

    private val job = UserProfileUpdateJob(userProfile)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { userProfile.enqueueUpdates() }
    }
}
