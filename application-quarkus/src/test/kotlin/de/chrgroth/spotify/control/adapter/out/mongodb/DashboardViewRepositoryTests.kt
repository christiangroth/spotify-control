package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.port.out.readmodel.DashboardViewRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class DashboardViewRepositoryTests {

  @Inject
  lateinit var dashboardViewRepository: DashboardViewRepositoryPort

  @Inject
  lateinit var dashboardViewDocumentRepository: DashboardViewDocumentRepository

  @BeforeEach
  fun cleanUp() {
    dashboardViewDocumentRepository.deleteAll()
  }

  private fun buildStats() = DashboardStats(
    syncedPlaylists = 3L,
    totalPlaylists = 5L,
    playlistCheckStats = PlaylistCheckStats(succeededChecks = 2L, totalChecks = 3L, allSucceeded = false),
    totalPlaybackEvents = 100L,
    playbackEventsLast30Days = 42L,
    playbackEventsPerDay = listOf(
      DayCount(date = LocalDate(2026, 8, 1), count = 5L, heightPercent = 50, dateLabel = "01.08"),
    ),
    recentlyPlayedTracks = listOf(
      RecentlyPlayedItem(
        trackId = TrackId("track-1"),
        trackName = "Track One",
        artistIds = listOf(ArtistId("artist-1")),
        artistNames = listOf("Artist One"),
        playedAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        albumId = AlbumId("album-1"),
        albumName = "Album One",
        imageLink = "https://example.com/image.png",
        durationSeconds = 210L,
      ),
    ),
    listeningStats = ListeningStats(
      listenedMinutesLast30Days = 60L,
      topTracksLast30Days = listOf(TopEntry(name = "Track One", totalMinutes = 10L, artistName = "Artist One")),
      topArtistsLast30Days = listOf(TopEntry(name = "Artist One", totalMinutes = 20L)),
      topAlbumsLast30Days = listOf(TopEntry(name = "Album One", totalMinutes = 15L, artistName = "Artist One")),
    ),
    catalogStats = CatalogStats(artistCount = 8L, albumCount = 6L, trackCount = 40L, undecidedArtistCount = 2L, shallowArtistCount = 1L),
  )

  @Test
  fun `find returns null when no view has been saved yet`() {
    assertThat(dashboardViewRepository.find()).isNull()
  }

  @Test
  fun `save and find round-trips the dashboard stats`() {
    val stats = buildStats()

    dashboardViewRepository.save(stats)

    assertThat(dashboardViewRepository.find()).isEqualTo(stats)
  }

  @Test
  fun `save overwrites the previously stored dashboard stats`() {
    dashboardViewRepository.save(buildStats())

    val updated = DashboardStats.EMPTY
    dashboardViewRepository.save(updated)

    assertThat(dashboardViewRepository.find()).isEqualTo(updated)
  }
}
