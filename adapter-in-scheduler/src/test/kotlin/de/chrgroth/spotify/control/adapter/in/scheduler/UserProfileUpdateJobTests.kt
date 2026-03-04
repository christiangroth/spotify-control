package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class UserProfileUpdateJobTests {

    private val userProfileUpdate: UserProfileUpdatePort = mockk(relaxed = true)

    private val job = UserProfileUpdateJob(userProfileUpdate)

    @Test
    fun `run calls enqueueUpdates`() {
        job.run()

        verify { userProfileUpdate.enqueueUpdates() }
    }
}
