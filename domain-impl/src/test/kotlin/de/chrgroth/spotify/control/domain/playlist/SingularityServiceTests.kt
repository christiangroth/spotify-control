package de.chrgroth.spotify.control.domain.playlist

import arrow.core.right
import de.chrgroth.spotify.control.domain.error.SingularityError
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SingularityServiceTests {

  private val currentUserResolver: CurrentUserResolver = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val outboxPort: OutboxPort = mockk()

  private val service = SingularityService(
    currentUserResolver,
    playlistRepository,
    appTrackRepository,
    appArtistRepository,
    spotifyPlaylist,
    spotifyAccessToken,
    outboxPort,
  )

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val endPlaylistId = "end-of-the-road"
  private val stagingPlaylistId = "start-of-the-road"
  private val artistId = ArtistId("artist-1")

  private fun buildPlaylistInfo(id: String, type: PlaylistType?) = PlaylistInfo(
    spotifyPlaylistId = id,
    snapshotId = "snap-1",
    lastSnapshotIdSyncTime = Clock.System.now(),
    name = id,
    syncStatus = PlaylistSyncStatus.ACTIVE,
    type = type,
  )

  private fun buildPlaylistTrack(trackId: String, artistId: String) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = listOf(ArtistId(artistId)),
    albumId = null,
  )

  private fun buildAppTrack(trackId: String, title: String, artistId: String, artistName: String? = null) = AppTrack(
    id = TrackId(trackId),
    title = title,
    artistId = ArtistId(artistId),
    artistName = artistName,
    lastSync = Instant.fromEpochMilliseconds(0),
  )

  private fun buildAppArtist(
    id: ArtistId,
    name: String,
    currentTrackId: TrackId? = null,
    currentTrackAddedAt: Instant? = null,
  ) = AppArtist(
    id = id,
    artistName = name,
    lastSync = Instant.fromEpochMilliseconds(0),
    singularityCurrentTrackId = currentTrackId,
    singularityCurrentTrackAddedAt = currentTrackAddedAt,
  )

  private fun mockPlaylists(endTracks: List<PlaylistTrack>, stagingTracks: List<PlaylistTrack>) {
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(endPlaylistId, PlaylistType.SINGULARITY),
      buildPlaylistInfo(stagingPlaylistId, PlaylistType.SINGULARITY_STAGING),
    )
    every { playlistRepository.findByPlaylistId(endPlaylistId) } returns Playlist(endPlaylistId, endTracks)
    every { playlistRepository.findByPlaylistId(stagingPlaylistId) } returns Playlist(stagingPlaylistId, stagingTracks)
  }

  @Test
  fun `getChallengerGroups returns empty list when no user resolved`() {
    every { currentUserResolver.userId() } returns null

    assertThat(service.getChallengerGroups()).isEmpty()
  }

  @Test
  fun `getChallengerGroups returns empty list when playlists are not configured`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo(endPlaylistId, PlaylistType.SINGULARITY))

    assertThat(service.getChallengerGroups()).isEmpty()
  }

  @Test
  fun `getChallengerGroups only includes staged tracks whose artist already has a current track`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("current-1", "artist-1")),
      stagingTracks = listOf(buildPlaylistTrack("challenger-1", "artist-1"), buildPlaylistTrack("unrelated-1", "artist-2")),
    )
    every { appTrackRepository.findByTrackIds(any()) } returns listOf(
      buildAppTrack("current-1", "Current Song", "artist-1", "Artist One"),
      buildAppTrack("challenger-1", "Challenger Song", "artist-1", "Artist One"),
    )
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(buildAppArtist(artistId, "Artist One"))

    val groups = service.getChallengerGroups()

    assertThat(groups).hasSize(1)
    assertThat(groups[0].artistId).isEqualTo("artist-1")
    assertThat(groups[0].artistName).isEqualTo("Artist One")
    assertThat(groups[0].currentTrack.title).isEqualTo("Current Song")
    assertThat(groups[0].challengers.map { it.title }).containsExactly("Challenger Song")
  }

  @Test
  fun `acceptChallenger returns error when track is not an open challenger`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("current-1", "artist-1")),
      stagingTracks = emptyList(),
    )

    val result = service.acceptChallenger("artist-1", "challenger-1")

    assertThat(result.isLeft()).isTrue()
    result.mapLeft { assertThat(it).isEqualTo(SingularityError.CHALLENGER_NOT_FOUND) }
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `acceptChallenger enqueues accept event for an open challenger`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("current-1", "artist-1")),
      stagingTracks = listOf(buildPlaylistTrack("challenger-1", "artist-1")),
    )
    every { outboxPort.enqueue(any()) } just runs

    val result = service.acceptChallenger("artist-1", "challenger-1")

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.AcceptSingularityChallenger("artist-1", "challenger-1")) }
  }

  @Test
  fun `discardChallenger enqueues discard event for an open challenger`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("current-1", "artist-1")),
      stagingTracks = listOf(buildPlaylistTrack("challenger-1", "artist-1")),
    )
    every { outboxPort.enqueue(any()) } just runs

    val result = service.discardChallenger("artist-1", "challenger-1")

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.DiscardSingularityChallenger("artist-1", "challenger-1")) }
  }

  @Test
  fun `handle AcceptSingularityChallenger replaces the current track, removes it from staging and updates tracking`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(endPlaylistId, PlaylistType.SINGULARITY),
      buildPlaylistInfo(stagingPlaylistId, PlaylistType.SINGULARITY_STAGING),
    )
    every { playlistRepository.findByPlaylistId(endPlaylistId) } returns Playlist(endPlaylistId, listOf(buildPlaylistTrack("current-1", "artist-1")))
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.replacePlaylistTrack(accessToken, endPlaylistId, "current-1", "challenger-1", 0) } returns Unit.right()
    every { spotifyPlaylist.removePlaylistTracks(accessToken, stagingPlaylistId, listOf("challenger-1")) } returns Unit.right()
    every { appArtistRepository.updateSingularityCurrentTrack(artistId, TrackId("challenger-1"), any()) } just runs
    every { appTrackRepository.findByTrackIds(setOf(TrackId("challenger-1"))) } returns listOf(buildAppTrack("challenger-1", "Challenger Song", "artist-1", "Artist One"))
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(buildAppArtist(artistId, "Artist One"))
    every { outboxPort.enqueue(any()) } just runs

    val result = service.handle(DomainOutboxEvent.AcceptSingularityChallenger("artist-1", "challenger-1"))

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.updateSingularityCurrentTrack(artistId, TrackId("challenger-1"), any()) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(endPlaylistId)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(stagingPlaylistId)) }
  }

  @Test
  fun `handle AcceptSingularityChallenger adds the track when the artist has no current track yet`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(endPlaylistId, PlaylistType.SINGULARITY),
      buildPlaylistInfo(stagingPlaylistId, PlaylistType.SINGULARITY_STAGING),
    )
    every { playlistRepository.findByPlaylistId(endPlaylistId) } returns Playlist(endPlaylistId, emptyList())
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.addPlaylistTracks(accessToken, endPlaylistId, listOf("challenger-1")) } returns Unit.right()
    every { spotifyPlaylist.removePlaylistTracks(accessToken, stagingPlaylistId, listOf("challenger-1")) } returns Unit.right()
    every { appArtistRepository.updateSingularityCurrentTrack(artistId, TrackId("challenger-1"), any()) } just runs
    every { appTrackRepository.findByTrackIds(setOf(TrackId("challenger-1"))) } returns emptyList()
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns emptyList()
    every { outboxPort.enqueue(any()) } just runs

    val result = service.handle(DomainOutboxEvent.AcceptSingularityChallenger("artist-1", "challenger-1"))

    assertThat(result.isRight()).isTrue()
    verify { spotifyPlaylist.addPlaylistTracks(accessToken, endPlaylistId, listOf("challenger-1")) }
  }

  @Test
  fun `handle DiscardSingularityChallenger only removes the track from staging`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(endPlaylistId, PlaylistType.SINGULARITY),
      buildPlaylistInfo(stagingPlaylistId, PlaylistType.SINGULARITY_STAGING),
    )
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.removePlaylistTracks(accessToken, stagingPlaylistId, listOf("challenger-1")) } returns Unit.right()
    every { appTrackRepository.findByTrackIds(setOf(TrackId("challenger-1"))) } returns listOf(buildAppTrack("challenger-1", "Challenger Song", "artist-1", "Artist One"))
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(buildAppArtist(artistId, "Artist One"))
    every { outboxPort.enqueue(any()) } just runs

    val result = service.handle(DomainOutboxEvent.DiscardSingularityChallenger("artist-1", "challenger-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appArtistRepository.updateSingularityCurrentTrack(any(), any(), any()) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(stagingPlaylistId)) }
  }

  @Test
  fun `handle ReconcileSingularityTracking is a no-op for playlists not part of the singularity workflow`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("other-playlist", PlaylistType.ALL))

    val result = service.handle(DomainOutboxEvent.ReconcileSingularityTracking("other-playlist"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appArtistRepository.updateSingularityCurrentTrack(any(), any(), any()) }
  }

  @Test
  fun `handle ReconcileSingularityTracking silently learns the current track when not tracked yet`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("current-1", "artist-1")),
      stagingTracks = emptyList(),
    )
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(buildAppArtist(artistId, "Artist One"))
    every { appArtistRepository.updateSingularityCurrentTrack(any(), any(), any()) } just runs

    val result = service.handle(DomainOutboxEvent.ReconcileSingularityTracking(endPlaylistId))

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.updateSingularityCurrentTrack(artistId, TrackId("current-1"), any()) }
  }

  @Test
  fun `handle ReconcileSingularityTracking reconciles a full manual swap`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("new-track", "artist-1")),
      stagingTracks = emptyList(),
    )
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(
      buildAppArtist(artistId, "Artist One", currentTrackId = TrackId("old-track")),
    )
    every { appArtistRepository.updateSingularityCurrentTrack(any(), any(), any()) } just runs

    val result = service.handle(DomainOutboxEvent.ReconcileSingularityTracking(stagingPlaylistId))

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.updateSingularityCurrentTrack(artistId, TrackId("new-track"), any()) }
  }

  @Test
  fun `handle ReconcileSingularityTracking does not reconcile a partial change where the new track is still staged`() {
    every { currentUserResolver.userId() } returns userId
    mockPlaylists(
      endTracks = listOf(buildPlaylistTrack("new-track", "artist-1")),
      stagingTracks = listOf(buildPlaylistTrack("new-track", "artist-1")),
    )
    every { appArtistRepository.findByArtistIds(setOf(artistId)) } returns listOf(
      buildAppArtist(artistId, "Artist One", currentTrackId = TrackId("old-track")),
    )

    val result = service.handle(DomainOutboxEvent.ReconcileSingularityTracking(endPlaylistId))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appArtistRepository.updateSingularityCurrentTrack(any(), any(), any()) }
  }
}
