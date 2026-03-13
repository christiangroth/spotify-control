package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReEnrichArtistNameBugfixStarterTests {

    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val syncPoolRepository: AppSyncPoolRepositoryPort = mockk()

    private val starter = ReEnrichArtistNameBugfixStarter(appArtistRepository, syncPoolRepository)

    @BeforeEach
    fun setUp() {
        every { syncPoolRepository.addArtists(any()) } just runs
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("ReEnrichArtistNameBugfix-v1")
    }

    @Test
    fun `no artists with imageLink and blank name - nothing added to pool`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns emptyList()

        starter.execute()

        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
    }

    @Test
    fun `artist with imageLink and blank name - added to sync pool`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns listOf(
            AppArtist(
                artistId = "a1",
                artistName = "",
                imageLink = "https://img.example.com/1.jpg",
                lastSync = Clock.System.now(),
            ),
        )

        starter.execute()

        verify(exactly = 1) { syncPoolRepository.addArtists(listOf("a1")) }
    }

    @Test
    fun `multiple artists with imageLink and blank name - all added to pool`() {
        every { appArtistRepository.findWithImageLinkAndBlankName() } returns listOf(
            AppArtist(artistId = "a1", artistName = "", imageLink = "https://img.example.com/1.jpg"),
            AppArtist(artistId = "a3", artistName = "", imageLink = "https://img.example.com/3.jpg"),
        )

        starter.execute()

        verify(exactly = 1) { syncPoolRepository.addArtists(listOf("a1", "a3")) }
    }
}
