package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.CatalogStats
import de.chrgroth.spotify.control.domain.model.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DashboardAdapterTests {

    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val appArtistRepository: AppArtistRepositoryPort = mockk()
    private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
    private val catalogBrowser: CatalogBrowserPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()

    private val adapter = DashboardAdapter(
        appPlaybackRepository, appTrackRepository, appArtistRepository, appAlbumRepository,
        catalogBrowser, playlistRepository, playlistCheckRepository,
        recentlyPlayedLimit = 5,
        topEntriesLimit = 3,
    )

    private val userId = UserId("user-1")
    private val syncTimestamp = Instant.fromEpochSeconds(0)

    private val artist1 = AppArtist(
        artistId = "artist-1", artistName = "Artist One",
        lastSync = syncTimestamp,
    )

    private val track1 = AppTrack(
        id = TrackId("track-1"), title = "Track One",
        artistId = ArtistId("artist-1"), durationMs = 300_000L,
        albumId = AlbumId("album-1"), lastSync = syncTimestamp,
    )

    private fun setupCommonMocks() {
        every { appPlaybackRepository.countAll(userId) } returns 10L
        every { appPlaybackRepository.countSince(userId, any()) } returns 5L
        every { appPlaybackRepository.countPerDaySince(userId, any()) } returns emptyList()
        every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns emptyList()
        every { playlistRepository.findByUserId(userId) } returns emptyList()
        every { playlistCheckRepository.countAll() } returns 0L
        every { playlistCheckRepository.countSucceeded() } returns 0L
        every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()
    }

    @Test
    fun `listening stats exclude items without tracked duration`() {
        setupCommonMocks()

        // Items without a tracked duration have secondsPlayed = 0
        val noDurationItem = AppPlaybackItem(
            userId = userId,
            playedAt = Instant.fromEpochSeconds(1000),
            trackId = "track-1",
            secondsPlayed = 0L,
        )
        // Items with a tracked duration have actual secondsPlayed > 0
        val appTrackedItem = AppPlaybackItem(
            userId = userId,
            playedAt = Instant.fromEpochSeconds(2000),
            trackId = "track-1",
            secondsPlayed = 180L, // 3 minutes
        )
        every { appPlaybackRepository.findAllSince(userId, any()) } returns listOf(noDurationItem, appTrackedItem)
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(artist1)
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

        val stats = adapter.getStats(userId)

        // Only the item with duration (180 seconds = 3 minutes) should count
        assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(3L)
    }

    @Test
    fun `listening stats are zero when all playback items have zero secondsPlayed`() {
        setupCommonMocks()

        val noDurationItem = AppPlaybackItem(
            userId = userId,
            playedAt = Instant.fromEpochSeconds(1000),
            trackId = "track-1",
            secondsPlayed = 0L,
        )
        every { appPlaybackRepository.findAllSince(userId, any()) } returns listOf(noDurationItem)
        every { appTrackRepository.findByTrackIds(any()) } returns listOf(track1)
        every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist1)

        val stats = adapter.getStats(userId)

        assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(0L)
        assertThat(stats.listeningStats.topTracksLast30Days).isEmpty()
        assertThat(stats.listeningStats.topArtistsLast30Days).isEmpty()
    }

    @Test
    fun `listening stats aggregate play durations across multiple tracks`() {
        setupCommonMocks()

        val track2 = AppTrack(
            id = TrackId("track-2"), title = "Track Two",
            artistId = ArtistId("artist-1"), durationMs = 240_000L,
            lastSync = syncTimestamp,
        )
        val items = listOf(
            AppPlaybackItem(userId = userId, playedAt = Instant.fromEpochSeconds(1000), trackId = "track-1", secondsPlayed = 0L),
            AppPlaybackItem(userId = userId, playedAt = Instant.fromEpochSeconds(2000), trackId = "track-1", secondsPlayed = 120L),
            AppPlaybackItem(userId = userId, playedAt = Instant.fromEpochSeconds(3000), trackId = "track-2", secondsPlayed = 180L),
        )
        every { appPlaybackRepository.findAllSince(userId, any()) } returns items
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns listOf(track1, track2)
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(artist1)
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

        val stats = adapter.getStats(userId)

        // track-1: 120s, track-2: 180s → total 300s = 5 minutes; item without duration (0s) ignored
        assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(5L)
        assertThat(stats.listeningStats.topTracksLast30Days).hasSize(2)
    }

    @Test
    fun `recently played tracks include duration from secondsPlayed`() {
        every { appPlaybackRepository.countAll(userId) } returns 1L
        every { appPlaybackRepository.countSince(userId, any()) } returns 1L
        every { appPlaybackRepository.countPerDaySince(userId, any()) } returns emptyList()
        every { appPlaybackRepository.findAllSince(userId, any()) } returns emptyList()
        every { playlistRepository.findByUserId(userId) } returns emptyList()
        every { playlistCheckRepository.countAll() } returns 0L
        every { playlistCheckRepository.countSucceeded() } returns 0L
        every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
        every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

        val playbackItem = AppPlaybackItem(
            userId = userId,
            playedAt = Instant.fromEpochSeconds(1000),
            trackId = "track-1",
            secondsPlayed = 210L,
        )
        every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns listOf(playbackItem)
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
        every { appArtistRepository.findByArtistIds(setOf("artist-1")) } returns listOf(artist1)

        val stats = adapter.getStats(userId)

        assertThat(stats.recentlyPlayedTracks).hasSize(1)
        assertThat(stats.recentlyPlayedTracks[0].durationSeconds).isEqualTo(210L)
        assertThat(stats.recentlyPlayedTracks[0].durationFormatted).isEqualTo("3:30")
    }
}
