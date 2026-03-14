package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.SyncError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.TrackSyncResult
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import kotlin.time.Instant
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

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("token")
    private val album1 = AppAlbum(id = AlbumId("album-1"), title = "Album One", artistId = ArtistId("artist-1"), artistName = "Artist One")
    private val trackWithAlbum1 = AppTrack(id = TrackId("track-1"), title = "Track One", albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"))
    private val trackWithAlbum2 = AppTrack(id = TrackId("track-2"), title = "Track Two", albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"))
    private val trackWithAlbum3 = AppTrack(id = TrackId("track-3"), title = "Track Three", albumId = AlbumId("album-2"), artistId = ArtistId("artist-2"))
    private val syncResult1 = TrackSyncResult(track = trackWithAlbum1, album = album1)
    private val syncResult2 = TrackSyncResult(track = trackWithAlbum2, album = album1)

    private fun buildUser(id: String = "user-1") = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Instant.DISTANT_FUTURE,
        lastLoginAt = Instant.fromEpochSeconds(0),
    )

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
        verify(exactly = 0) { syncPoolRepository.addAlbums(any()) }
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
        verify(exactly = 0) { syncPoolRepository.addAlbums(any()) }
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
        verify(exactly = 0) { syncPoolRepository.addAlbums(any()) }
    }

    @Test
    fun `resyncCatalog adds album IDs from tracks with known album ID to sync pool`() {
        every { appArtistRepository.findAll() } returns emptyList()
        every { appTrackRepository.findAll() } returns listOf(trackWithAlbum1, trackWithAlbum2, trackWithAlbum3)
        every { syncPoolRepository.addTracks(any()) } just runs
        every { syncPoolRepository.addAlbums(any()) } just runs

        val result = adapter.resyncCatalog()

        assertThat(result.isRight()).isTrue()
        verify { syncPoolRepository.addAlbums(match { it.containsAll(listOf("album-1", "album-2")) && it.size == 2 }) }
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
        verify(exactly = 0) { syncPoolRepository.addAlbums(any()) }
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

    // --- syncMissingTracks tests ---

    @Test
    fun `handle SyncMissingTracks returns success when no users available`() {
        every { userRepository.findAll() } returns emptyList()

        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
    }

    @Test
    fun `handle SyncMissingTracks returns success when task has no track IDs`() {
        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(emptyList()))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { userRepository.findAll() }
        verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
    }

    @Test
    fun `handle SyncMissingTracks uses album endpoint when tracks have albumId`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns
            listOf(trackWithAlbum1, trackWithAlbum2)
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns listOf(syncResult1, syncResult2).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.removeTracks(any()) } just runs
        every { syncPoolRepository.removeAlbums(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs

        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1", "track-2")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") }
        verify(exactly = 0) { spotifyCatalog.getTracks(any(), any(), any()) }
        verify { appTrackRepository.upsertAll(listOf(syncResult1.track, syncResult2.track)) }
        verify { appAlbumRepository.upsertAll(listOf(album1)) }
        verify { syncPoolRepository.removeTracks(match { it.containsAll(listOf("track-1", "track-2")) }) }
        verify { syncPoolRepository.removeAlbums(listOf("album-1")) }
    }

    @Test
    fun `handle SyncMissingTracks stores all album tracks even when only some were requested`() {
        val extraTrack = AppTrack(id = TrackId("track-99"), title = "Extra Track", albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"))
        val extraResult = TrackSyncResult(track = extraTrack, album = album1)
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(trackWithAlbum1)
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns
            listOf(syncResult1, syncResult2, extraResult).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.removeTracks(any()) } just runs
        every { syncPoolRepository.removeAlbums(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs

        adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1")))

        verify { appTrackRepository.upsertAll(listOf(syncResult1.track, syncResult2.track, extraResult.track)) }
        verify { syncPoolRepository.removeTracks(listOf("track-1")) }
        verify { syncPoolRepository.removeAlbums(listOf("album-1")) }
    }

    @Test
    fun `handle SyncMissingTracks uses direct endpoint when tracks have no albumId`() {
        val trackWithoutAlbum = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"))
        val directResult = TrackSyncResult(track = trackWithAlbum1, album = album1)
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(trackWithoutAlbum)
        every { spotifyCatalog.getTracks(userId, accessToken, listOf("track-1")) } returns listOf(directResult).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.removeTracks(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addAlbums(any()) } just runs

        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { spotifyCatalog.getAlbumTracks(any(), any(), any()) }
        verify { spotifyCatalog.getTracks(userId, accessToken, listOf("track-1")) }
        verify { syncPoolRepository.addAlbums(listOf("album-1")) }
    }

    @Test
    fun `handle SyncMissingTracks falls back to direct endpoint for tracks not returned by album`() {
        val missingTrack = AppTrack(id = TrackId("track-missing"), title = "Missing", albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"))
        val missingDirectResult = TrackSyncResult(
            track = missingTrack.copy(albumId = AlbumId("album-1")),
            album = album1,
        )
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-missing"))) } returns
            listOf(trackWithAlbum1, missingTrack)
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns listOf(syncResult1).right()
        every { spotifyCatalog.getTracks(userId, accessToken, listOf("track-missing")) } returns listOf(missingDirectResult).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.removeTracks(any()) } just runs
        every { syncPoolRepository.removeAlbums(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.addAlbums(any()) } just runs

        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1", "track-missing")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") }
        verify { spotifyCatalog.getTracks(userId, accessToken, listOf("track-missing")) }
        verify { syncPoolRepository.removeTracks(listOf("track-1")) }
        verify { syncPoolRepository.removeTracks(listOf("track-missing")) }
        verify { syncPoolRepository.removeAlbums(listOf("album-1")) }
    }

    @Test
    fun `handle SyncMissingTracks returns failed when album endpoint returns error`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(trackWithAlbum1)
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns SyncError.TRACK_DETAILS_FETCH_FAILED.left()

        val result = adapter.handle(DomainOutboxEvent.SyncMissingTracks(listOf("track-1")))

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
    }

    // --- syncMissingAlbums tests ---

    @Test
    fun `handle SyncMissingAlbums returns success when no users available`() {
        every { userRepository.findAll() } returns emptyList()

        val result = adapter.handle(DomainOutboxEvent.SyncMissingAlbums(listOf("album-1")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { spotifyCatalog.getAlbumTracks(any(), any(), any()) }
    }

    @Test
    fun `handle SyncMissingAlbums returns success when task has no album IDs`() {
        val result = adapter.handle(DomainOutboxEvent.SyncMissingAlbums(emptyList()))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { userRepository.findAll() }
        verify(exactly = 0) { spotifyCatalog.getAlbumTracks(any(), any(), any()) }
    }

    @Test
    fun `handle SyncMissingAlbums syncs albums and upserts all tracks`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns listOf(syncResult1, syncResult2).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.removeAlbums(any()) } just runs

        val result = adapter.handle(DomainOutboxEvent.SyncMissingAlbums(listOf("album-1")))

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") }
        verify { appTrackRepository.upsertAll(listOf(syncResult1.track, syncResult2.track)) }
        verify { appAlbumRepository.upsertAll(listOf(album1)) }
        verify { syncPoolRepository.removeAlbums(listOf("album-1")) }
    }

    @Test
    fun `handle SyncMissingAlbums adds artist IDs to sync pool`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns listOf(syncResult1, syncResult2).right()
        every { appTrackRepository.upsertAll(any()) } just runs
        every { appAlbumRepository.upsertAll(any()) } just runs
        every { syncPoolRepository.addArtists(any()) } just runs
        every { syncPoolRepository.removeAlbums(any()) } just runs

        adapter.handle(DomainOutboxEvent.SyncMissingAlbums(listOf("album-1")))

        verify { syncPoolRepository.addArtists(listOf("artist-1")) }
    }

    @Test
    fun `handle SyncMissingAlbums returns failed when album endpoint returns error`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCatalog.getAlbumTracks(userId, accessToken, "album-1") } returns SyncError.TRACK_DETAILS_FETCH_FAILED.left()

        val result = adapter.handle(DomainOutboxEvent.SyncMissingAlbums(listOf("album-1")))

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
    }
}
