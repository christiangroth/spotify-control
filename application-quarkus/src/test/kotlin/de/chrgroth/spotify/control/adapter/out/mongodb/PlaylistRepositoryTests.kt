package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistRepositoryTests {

    @Inject
    lateinit var playlistRepository: PlaylistRepositoryPort

    private fun buildPlaylistInfo(id: String, syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.PASSIVE): PlaylistInfo {
        val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
        return PlaylistInfo(
            spotifyPlaylistId = id,
            snapshotId = "snap-1",
            lastSnapshotIdSyncTime = now - 1.hours,
            name = "Playlist $id",
            syncStatus = syncStatus,
        )
    }

    @Test
    fun `findByUserId returns empty list when no playlists exist`() {
        val userId = UserId("no-playlists-${UUID.randomUUID()}")

        assertThat(playlistRepository.findByUserId(userId)).isEmpty()
    }

    @Test
    fun `saveAll and findByUserId round-trips playlists correctly`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlists = listOf(
            buildPlaylistInfo("p1", PlaylistSyncStatus.ACTIVE),
            buildPlaylistInfo("p2", PlaylistSyncStatus.PASSIVE),
        )

        playlistRepository.saveAll(userId, playlists)

        val found = playlistRepository.findByUserId(userId)
        assertThat(found).hasSize(2)
        val byId = found.associateBy { it.spotifyPlaylistId }
        assertThat(byId["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
        assertThat(byId["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    }

    @Test
    fun `saveAll replaces previous playlists for the same user`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        playlistRepository.saveAll(userId, listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2")))

        playlistRepository.saveAll(userId, listOf(buildPlaylistInfo("p3")))

        val found = playlistRepository.findByUserId(userId)
        assertThat(found).hasSize(1)
        assertThat(found[0].spotifyPlaylistId).isEqualTo("p3")
    }

    @Test
    fun `saveAll with empty list removes all playlists for user`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        playlistRepository.saveAll(userId, listOf(buildPlaylistInfo("p1")))

        playlistRepository.saveAll(userId, emptyList())

        assertThat(playlistRepository.findByUserId(userId)).isEmpty()
    }

    @Test
    fun `saveAll does not affect playlists of other users`() {
        val userId1 = UserId("test-${UUID.randomUUID()}")
        val userId2 = UserId("test-${UUID.randomUUID()}")
        playlistRepository.saveAll(userId1, listOf(buildPlaylistInfo("p1")))
        playlistRepository.saveAll(userId2, listOf(buildPlaylistInfo("p2")))

        playlistRepository.saveAll(userId1, listOf(buildPlaylistInfo("p3")))

        assertThat(playlistRepository.findByUserId(userId1).map { it.spotifyPlaylistId }).containsExactly("p3")
        assertThat(playlistRepository.findByUserId(userId2).map { it.spotifyPlaylistId }).containsExactly("p2")
    }
}
