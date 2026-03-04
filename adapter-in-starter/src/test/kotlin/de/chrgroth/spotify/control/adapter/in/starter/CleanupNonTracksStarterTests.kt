package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CleanupNonTracksStarterTests {

    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()
    private val starter = CleanupNonTracksStarter(recentlyPlayedRepository)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("CleanupNonTracksStarter-v1")
    }

    @Test
    fun `execute deletes non-tracks and logs result`() {
        every { recentlyPlayedRepository.deleteNonTracks() } returns 3L

        starter.execute()

        verify(exactly = 1) { recentlyPlayedRepository.deleteNonTracks() }
    }

    @Test
    fun `execute handles zero deletions without throwing`() {
        every { recentlyPlayedRepository.deleteNonTracks() } returns 0L

        starter.execute()

        verify(exactly = 1) { recentlyPlayedRepository.deleteNonTracks() }
    }
}
