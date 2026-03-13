package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class SyncArtistPlaybackFromPlaylistsTests {

    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyCatalog: SpotifyCatalogPort = mockk()
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk(relaxed = true)
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk(relaxed = true)
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
    private val userRepository: UserRepositoryPort = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk(relaxed = true)

    private val adapter = CatalogAdapter(
        spotifyAccessToken,
        spotifyCatalog,
        appArtistRepository,
        appTrackRepository,
        appAlbumRepository,
        appPlaybackRepository,
        userRepository,
        playlistRepository,
        outboxPort,
    )

    private val userId = UserId("user-1")

    private fun artist(id: String, status: ArtistPlaybackProcessingStatus) =
        AppArtist(artistId = id, artistName = "Artist $id", playbackProcessingStatus = status)

    private fun setupActivePlaylistArtists(vararg artistIds: String) {
        every { playlistRepository.findArtistIdsInActivePlaylists() } returns artistIds.toSet()
    }

    private fun setupArtistsByStatus(
        undecided: List<AppArtist> = emptyList(),
        active: List<AppArtist> = emptyList(),
        inactive: List<AppArtist> = emptyList(),
    ) {
        every { appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.UNDECIDED) } returns undecided
        every { appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.ACTIVE) } returns active
        every { appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.INACTIVE) } returns inactive
    }

    private fun mockFindById(artistId: String, status: ArtistPlaybackProcessingStatus) {
        every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(artist(artistId, status))
    }

    @Test
    fun `undecided artist in active playlist is set to ACTIVE`() {
        val artistId = "artist-undecided-active"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(undecided = listOf(artist(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)))
        mockFindById(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)
        every { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE) } just runs

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE) }
    }

    @Test
    fun `undecided artist not in active playlist is set to INACTIVE`() {
        val artistId = "artist-undecided-inactive"
        setupActivePlaylistArtists()
        setupArtistsByStatus(undecided = listOf(artist(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)))
        mockFindById(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)
        every { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE) } just runs

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE) }
    }

    @Test
    fun `active artist not in any active playlist is set to INACTIVE`() {
        val artistId = "artist-active-removed"
        setupActivePlaylistArtists()
        setupArtistsByStatus(active = listOf(artist(artistId, ArtistPlaybackProcessingStatus.ACTIVE)))
        mockFindById(artistId, ArtistPlaybackProcessingStatus.ACTIVE)
        every { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE) } just runs

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE) }
    }

    @Test
    fun `active artist still in active playlist is not changed`() {
        val artistId = "artist-active-kept"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(active = listOf(artist(artistId, ArtistPlaybackProcessingStatus.ACTIVE)))

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify(exactly = 0) { appArtistRepository.updatePlaybackProcessingStatus(artistId, any()) }
    }

    @Test
    fun `inactive artist in active playlist is set to ACTIVE`() {
        val artistId = "artist-inactive-reactivated"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(inactive = listOf(artist(artistId, ArtistPlaybackProcessingStatus.INACTIVE)))
        mockFindById(artistId, ArtistPlaybackProcessingStatus.INACTIVE)
        every { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE) } just runs

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { appArtistRepository.updatePlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE) }
    }

    @Test
    fun `inactive artist not in any active playlist is not changed`() {
        val artistId = "artist-inactive-kept"
        setupActivePlaylistArtists()
        setupArtistsByStatus(inactive = listOf(artist(artistId, ArtistPlaybackProcessingStatus.INACTIVE)))

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify(exactly = 0) { appArtistRepository.updatePlaybackProcessingStatus(artistId, any()) }
    }

    @Test
    fun `multiple artists across all statuses are handled correctly`() {
        val undecidedInPlaylist = "artist-undecided-in"
        val undecidedNotInPlaylist = "artist-undecided-out"
        val activeInPlaylist = "artist-active-in"
        val activeNotInPlaylist = "artist-active-out"
        val inactiveInPlaylist = "artist-inactive-in"
        val inactiveNotInPlaylist = "artist-inactive-out"

        setupActivePlaylistArtists(undecidedInPlaylist, activeInPlaylist, inactiveInPlaylist)
        setupArtistsByStatus(
            undecided = listOf(
                artist(undecidedInPlaylist, ArtistPlaybackProcessingStatus.UNDECIDED),
                artist(undecidedNotInPlaylist, ArtistPlaybackProcessingStatus.UNDECIDED),
            ),
            active = listOf(
                artist(activeInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE),
                artist(activeNotInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE),
            ),
            inactive = listOf(
                artist(inactiveInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE),
                artist(inactiveNotInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE),
            ),
        )
        mockFindById(undecidedInPlaylist, ArtistPlaybackProcessingStatus.UNDECIDED)
        mockFindById(undecidedNotInPlaylist, ArtistPlaybackProcessingStatus.UNDECIDED)
        mockFindById(activeNotInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE)
        mockFindById(inactiveInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE)
        every { appArtistRepository.updatePlaybackProcessingStatus(any(), any()) } just runs

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { appArtistRepository.updatePlaybackProcessingStatus(undecidedInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE) }
        verify { appArtistRepository.updatePlaybackProcessingStatus(undecidedNotInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE) }
        verify(exactly = 0) { appArtistRepository.updatePlaybackProcessingStatus(activeInPlaylist, any()) }
        verify { appArtistRepository.updatePlaybackProcessingStatus(activeNotInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE) }
        verify { appArtistRepository.updatePlaybackProcessingStatus(inactiveInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE) }
        verify(exactly = 0) { appArtistRepository.updatePlaybackProcessingStatus(inactiveNotInPlaylist, any()) }
    }

    @Test
    fun `no changes when all artists have correct status`() {
        val activeInPlaylist = "artist-active-in"
        val inactiveOutOfPlaylist = "artist-inactive-out"

        setupActivePlaylistArtists(activeInPlaylist)
        setupArtistsByStatus(
            active = listOf(artist(activeInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE)),
            inactive = listOf(artist(inactiveOutOfPlaylist, ArtistPlaybackProcessingStatus.INACTIVE)),
        )

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify(exactly = 0) { appArtistRepository.updatePlaybackProcessingStatus(any(), any()) }
    }
}
