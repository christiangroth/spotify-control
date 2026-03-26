package de.chrgroth.spotify.control.domain

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class SyncArtistPlaybackFromPlaylistsTests {

    private val userRepository: UserRepositoryPort = mockk(relaxed = true)
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk(relaxed = true)
    private val spotifyPlayback: SpotifyPlaybackPort = mockk(relaxed = true)
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val syncController: SyncController = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk(relaxed = true)
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
    private val playbackState: PlaybackStatePort = mockk(relaxed = true)
    private val catalog: CatalogPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()

    private val adapter = PlaybackAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyPlayback,
        currentlyPlayingRepository,
        recentlyPlayedRepository,
        recentlyPartialPlayedRepository,
        appPlaybackRepository,
        appArtistRepository,
        syncController,
        outboxPort,
        dashboardRefresh,
        playbackState,
        catalog,
        playlistRepository,
        minimumProgressSeconds = 25L,
    )

    private val userId = UserId("user-1")

    private fun artist(id: String, status: ArtistPlaybackProcessingStatus) =
        AppArtist(id = ArtistId(id), artistName = "Artist $id", playbackProcessingStatus = status, lastSync = kotlin.time.Instant.fromEpochSeconds(1))

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

    private fun mockCatalogUpdate(artistId: String, newStatus: ArtistPlaybackProcessingStatus) {
        every { catalog.updateArtistPlaybackProcessingStatus(artistId, newStatus, userId) } returns Unit.right()
    }

    @Test
    fun `undecided artist in active playlist is set to ACTIVE`() {
        val artistId = "artist-undecided-active"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(undecided = listOf(artist(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)))
        mockCatalogUpdate(artistId, ArtistPlaybackProcessingStatus.ACTIVE)

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { catalog.updateArtistPlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE, userId) }
    }

    @Test
    fun `undecided artist not in active playlist is set to INACTIVE`() {
        val artistId = "artist-undecided-inactive"
        setupActivePlaylistArtists()
        setupArtistsByStatus(undecided = listOf(artist(artistId, ArtistPlaybackProcessingStatus.UNDECIDED)))
        mockCatalogUpdate(artistId, ArtistPlaybackProcessingStatus.INACTIVE)

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { catalog.updateArtistPlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE, userId) }
    }

    @Test
    fun `active artist not in any active playlist is set to INACTIVE`() {
        val artistId = "artist-active-removed"
        setupActivePlaylistArtists()
        setupArtistsByStatus(active = listOf(artist(artistId, ArtistPlaybackProcessingStatus.ACTIVE)))
        mockCatalogUpdate(artistId, ArtistPlaybackProcessingStatus.INACTIVE)

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { catalog.updateArtistPlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.INACTIVE, userId) }
    }

    @Test
    fun `active artist still in active playlist is not changed`() {
        val artistId = "artist-active-kept"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(active = listOf(artist(artistId, ArtistPlaybackProcessingStatus.ACTIVE)))

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify(exactly = 0) { catalog.updateArtistPlaybackProcessingStatus(artistId, any(), any()) }
    }

    @Test
    fun `inactive artist in active playlist is set to ACTIVE`() {
        val artistId = "artist-inactive-reactivated"
        setupActivePlaylistArtists(artistId)
        setupArtistsByStatus(inactive = listOf(artist(artistId, ArtistPlaybackProcessingStatus.INACTIVE)))
        mockCatalogUpdate(artistId, ArtistPlaybackProcessingStatus.ACTIVE)

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { catalog.updateArtistPlaybackProcessingStatus(artistId, ArtistPlaybackProcessingStatus.ACTIVE, userId) }
    }

    @Test
    fun `inactive artist not in any active playlist is not changed`() {
        val artistId = "artist-inactive-kept"
        setupActivePlaylistArtists()
        setupArtistsByStatus(inactive = listOf(artist(artistId, ArtistPlaybackProcessingStatus.INACTIVE)))

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify(exactly = 0) { catalog.updateArtistPlaybackProcessingStatus(artistId, any(), any()) }
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
        every { catalog.updateArtistPlaybackProcessingStatus(any(), any(), any()) } returns Unit.right()

        adapter.syncArtistPlaybackFromPlaylists(userId)

        verify { catalog.updateArtistPlaybackProcessingStatus(undecidedInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE, userId) }
        verify { catalog.updateArtistPlaybackProcessingStatus(undecidedNotInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE, userId) }
        verify(exactly = 0) { catalog.updateArtistPlaybackProcessingStatus(activeInPlaylist, any(), any()) }
        verify { catalog.updateArtistPlaybackProcessingStatus(activeNotInPlaylist, ArtistPlaybackProcessingStatus.INACTIVE, userId) }
        verify { catalog.updateArtistPlaybackProcessingStatus(inactiveInPlaylist, ArtistPlaybackProcessingStatus.ACTIVE, userId) }
        verify(exactly = 0) { catalog.updateArtistPlaybackProcessingStatus(inactiveNotInPlaylist, any(), any()) }
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

        verify(exactly = 0) { catalog.updateArtistPlaybackProcessingStatus(any(), any(), any()) }
    }
}
