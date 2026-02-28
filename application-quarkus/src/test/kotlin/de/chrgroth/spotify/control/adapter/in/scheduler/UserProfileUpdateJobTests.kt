package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify

@QuarkusTest
class UserProfileUpdateJobTests {

    @InjectMock
    lateinit var userProfileUpdate: UserProfileUpdatePort

    @Inject
    lateinit var job: UserProfileUpdateJob

    @Test
    fun `run calls updateUserProfiles`() {
        job.run()

        verify(userProfileUpdate).updateUserProfiles()
    }
}
