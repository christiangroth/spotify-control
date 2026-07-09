package de.chrgroth.spotify.control.domain.catalog

import arrow.core.right
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlaybackEnrichmentServiceTests {

  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyCatalog: SpotifyCatalogPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
  private val currentUserResolver: CurrentUserResolver = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
  private val syncController: SyncController = mockk(relaxed = true)
  private val playbackAggregation: de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort = mockk(relaxed = true)
  private val syncTraceRepository: SyncTraceRepositoryPort = mockk(relaxed = true)

  private val adapter = CatalogService(
    spotifyAccessToken,
    spotifyCatalog,
    appArtistRepository,
    appTrackRepository,
    appAlbumRepository,
    recentlyPlayedRepository,
    recentlyPartialPlayedRepository,
    currentUserResolver,
    outboxPort,
    playlistRepository,
    playlistCheckRepository,
    dashboardRefresh,
    syncController,
    playbackAggregation,
    syncTraceRepository,
  )

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("access-token")

  @Test
  fun `syncArtistDetails stores artist from Spotify response`() {
    val artistId = "artist-1"
    val spotifyArtist = AppArtist(
      id = ArtistId(artistId),
      artistName = "Real Artist Name",
      imageLink = "https://example.com/image.jpg",
      type = "artist",
      lastSync = kotlin.time.Instant.fromEpochSeconds(1),
    )
    every { appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))) } returns emptyList()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtist(accessToken, artistId) } returns spotifyArtist.right()
    every { appArtistRepository.upsertAll(listOf(spotifyArtist)) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncArtistDetails(artistId)

    verify { appArtistRepository.upsertAll(listOf(spotifyArtist)) }
    verify { outboxPort.enqueue(de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent.SyncArtistAlbums(artistId)) }
  }

  @Test
  fun `syncArtistDetails skips update when artist already in catalog`() {
    val artistId = "artist-already-synced"
    val syncedArtist = AppArtist(
      id = ArtistId(artistId),
      artistName = "Known Artist",
      lastSync = kotlin.time.Clock.System.now(),
    )
    every { appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))) } returns listOf(syncedArtist)

    adapter.syncArtistDetails(artistId)

    verify(exactly = 0) { spotifyCatalog.getArtist(any(), any()) }
    verify(exactly = 0) { appArtistRepository.upsertAll(any()) }
  }
}
