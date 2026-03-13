package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogAdapterTests {

    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyCatalog: SpotifyCatalogPort = mockk()
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
    private val userRepository: UserRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()
    private val syncPoolRepository: AppSyncPoolRepositoryPort = mockk()

    private val adapter = CatalogAdapter(
        spotifyAccessToken, spotifyCatalog,
        appArtistRepository, appTrackRepository, appAlbumRepository,
        appPlaybackRepository, userRepository, outboxPort, syncPoolRepository,
    )

    private val artist1 = AppArtist(artistId = "artist-1", artistName = "Artist One", playbackProcessingStatus = ArtistPlaybackProcessingStatus.UNDECIDED)
    private val artist2 = AppArtist(artistId = "artist-2", artistName = "Artist Two", playbackProcessingStatus = ArtistPlaybackProcessingStatus.UNDECIDED)
    private val track1 = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"))
    private val track2 = AppTrack(id = TrackId("track-2"), title = "Track Two", artistId = ArtistId("artist-2"))

    // --- resyncArtist tests ---

    @Test
    fun `resyncArtist returns error when artist not found`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns emptyList()

        val result = adapter.resyncArtist("artist-1")

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `resyncArtist adds artist and their tracks to sync pool`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(artist1)
        every { appTrackRepository.findByArtistId(ArtistId("artist-1")) } returns listOf(track1)
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        val result = adapter.resyncArtist("artist-1")

        assertThat(result.isRight()).isTrue()
        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify { syncPoolRepository.addTracks(listOf("track-1")) }
    }

    @Test
    fun `resyncArtist adds artist to sync pool when they have no tracks`() {
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(artist1)
        every { appTrackRepository.findByArtistId(ArtistId("artist-1")) } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs

        val result = adapter.resyncArtist("artist-1")

        assertThat(result.isRight()).isTrue()
        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    // --- resyncCatalog tests ---

    @Test
    fun `resyncCatalog does nothing when catalog is empty`() {
        every { appArtistRepository.findAll() } returns emptyList()
        every { appTrackRepository.findAll() } returns emptyList()

        val result = adapter.resyncCatalog()

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `resyncCatalog adds all artists to sync pool`() {
        every { appArtistRepository.findAll() } returns listOf(artist1, artist2)
        every { appTrackRepository.findAll() } returns emptyList()
        every { syncPoolRepository.addArtists(any()) } just runs

        val result = adapter.resyncCatalog()

        assertThat(result.isRight()).isTrue()
        verify { syncPoolRepository.addArtists(listOf("artist-1", "artist-2")) }
        verify(exactly = 0) { syncPoolRepository.addTracks(any()) }
    }

    @Test
    fun `resyncCatalog adds all tracks to sync pool`() {
        every { appArtistRepository.findAll() } returns emptyList()
        every { appTrackRepository.findAll() } returns listOf(track1, track2)
        every { syncPoolRepository.addTracks(any()) } just runs

        val result = adapter.resyncCatalog()

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { syncPoolRepository.addArtists(any()) }
        verify { syncPoolRepository.addTracks(listOf("track-1", "track-2")) }
    }

    @Test
    fun `resyncCatalog adds both artists and tracks to sync pool`() {
        every { appArtistRepository.findAll() } returns listOf(artist1, artist2)
        every { appTrackRepository.findAll() } returns listOf(track1, track2)
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        val result = adapter.resyncCatalog()

        assertThat(result.isRight()).isTrue()
        verify { syncPoolRepository.addArtists(listOf("artist-1", "artist-2")) }
        verify { syncPoolRepository.addTracks(listOf("track-1", "track-2")) }
    }

    // --- handle(ResyncCatalog) tests ---

    @Test
    fun `handle ResyncCatalog returns success`() {
        every { appArtistRepository.findAll() } returns listOf(artist1)
        every { appTrackRepository.findAll() } returns listOf(track1)
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addTracks(any()) } just runs

        val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
    }

    @Test
    fun `handle ResyncCatalog returns success when catalog is empty`() {
        every { appArtistRepository.findAll() } returns emptyList()
        every { appTrackRepository.findAll() } returns emptyList()

        val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
    }
}
