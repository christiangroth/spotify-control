package de.chrgroth.spotify.control.domain.playback

import arrow.core.right
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Test

class PlaybackPollingTests {

  private val currentUserResolver: CurrentUserResolver = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyPlayback: SpotifyPlaybackPort = mockk()
  private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)

  private val service = PlaybackService(
    currentUserResolver,
    spotifyAccessToken,
    spotifyPlayback,
    currentlyPlayingRepository,
    recentlyPlayedRepository,
    recentlyPartialPlayedRepository,
    mockk<AppPlaybackRepositoryPort>(relaxed = true),
    mockk<SyncController>(relaxed = true),
    mockk<OutboxPort>(relaxed = true),
    mockk<DashboardRefreshPort>(relaxed = true),
    mockk<PlaybackStatePort>(relaxed = true),
    SimpleMeterRegistry(),
    minimumProgressSeconds = 25L,
  )

  private val accessToken = AccessToken("token")

  init {
    every { currentUserResolver.userId() } returns UserId("user-1")
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(accessToken) } returns null.right()
    every { spotifyPlayback.getRecentlyPlayed(accessToken, any()) } returns emptyList<RecentlyPlayedItem>().right()
    every { currentlyPlayingRepository.findAll() } returns emptyList()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(any()) } returns emptySet()
  }

  @Test
  fun `fetches recently played on the first poll but not again on the next poll within the interval`() {
    service.fetchPlaybackData()
    service.fetchPlaybackData()

    verify(exactly = 2) { spotifyPlayback.getCurrentlyPlaying(accessToken) }
    verify(exactly = 1) { spotifyPlayback.getRecentlyPlayed(accessToken, any()) }
  }

  @Test
  fun `fetches recently played right away after a track change produced a provisional partial play`() {
    service.fetchPlaybackData()

    val now = Clock.System.now()
    val skippedTrack = CurrentlyPlayingItem(
      trackId = TrackId("skipped"),
      trackName = "Skipped",
      artistIds = listOf(ArtistId("artist")),
      artistNames = listOf("Artist"),
      progressMs = 40_000L,
      durationMs = 200_000L,
      isPlaying = true,
      observedAt = now,
      startTime = now - 40_000.milliseconds,
    )
    every { currentlyPlayingRepository.findAll() } returns listOf(skippedTrack)
    service.fetchPlaybackData()

    verify(exactly = 2) { spotifyPlayback.getRecentlyPlayed(accessToken, any()) }
  }
}
