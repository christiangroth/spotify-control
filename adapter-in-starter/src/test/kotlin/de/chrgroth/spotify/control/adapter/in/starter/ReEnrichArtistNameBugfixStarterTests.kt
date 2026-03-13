package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReEnrichArtistNameBugfixStarterTests {

    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val userRepository: UserRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()

    private val starter = ReEnrichArtistNameBugfixStarter(appArtistRepository, userRepository, outboxPort)

    private val userId = UserId("user-1")
    private val user = User(
        spotifyUserId = userId,
        displayName = "Test User",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Instant.fromEpochMilliseconds(0),
        lastLoginAt = Instant.fromEpochMilliseconds(0),
    )

    @BeforeEach
    fun setUp() {
        every { userRepository.findAll() } returns listOf(user)
        justRun { outboxPort.enqueue(any()) }
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("ReEnrichArtistNameBugfix-v1")
    }

    @Test
    fun `no artists with imageLink and blank name - no events enqueued`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns emptyList()

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `artist with imageLink and blank name - sync enqueued`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns listOf(
            AppArtist(
                artistId = "a1",
                artistName = "",
                imageLink = "https://img.example.com/1.jpg",
                lastSync = Clock.System.now(),
            ),
        )

        starter.execute()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("a1", userId)) }
    }

    @Test
    fun `artist with imageLink and blank name but no users - no events enqueued`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns listOf(
            AppArtist(artistId = "a1", artistName = "", imageLink = "https://img.example.com/1.jpg"),
        )
        every { userRepository.findAll() } returns emptyList()

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `multiple artists with imageLink and blank name - all enqueued`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns listOf(
            AppArtist(artistId = "a1", artistName = "", imageLink = "https://img.example.com/1.jpg"),
            AppArtist(artistId = "a3", artistName = "", imageLink = "https://img.example.com/3.jpg"),
        )

        starter.execute()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("a1", userId)) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("a3", userId)) }
    }
}
