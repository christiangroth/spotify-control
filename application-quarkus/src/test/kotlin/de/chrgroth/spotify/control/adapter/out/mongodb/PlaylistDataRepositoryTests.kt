package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistDataRepositoryTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  @Inject
  lateinit var playlistDocumentRepository: PlaylistDocumentRepository

  private fun buildPlaylist(playlistId: String) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = listOf(
      PlaylistTrack(
        trackId = TrackId("track-1"),
        artistIds = listOf(ArtistId("artist-1")),
        albumId = AlbumId("album-1"),
      ),
    ),
  )

  @Test
  fun `findByPlaylistId returns null when no playlist exists`() {
    assertThat(playlistRepository.findByPlaylistId("unknown-playlist-${UUID.randomUUID()}")).isNull()
  }

  @Test
  fun `save and findByPlaylistId round-trips playlist correctly`() {
    val playlistId = "playlist-${UUID.randomUUID()}"
    val playlist = buildPlaylist(playlistId)

    playlistRepository.save(playlist)

    val found = playlistRepository.findByPlaylistId(playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.spotifyPlaylistId).isEqualTo(playlistId)
    assertThat(found.tracks).hasSize(1)
    assertThat(found.tracks[0].trackId).isEqualTo(TrackId("track-1"))
    assertThat(found.tracks[0].artistIds).containsExactly(ArtistId("artist-1"))
  }

  @Test
  fun `numberOfTracks returns correct size after save`() {
    val playlistId = "playlist-ntracks-${UUID.randomUUID()}"
    val playlist = Playlist(
      spotifyPlaylistId = playlistId,
      tracks = listOf(
        PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1")), albumId = AlbumId("al1")),
        PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
      ),
    )

    playlistRepository.save(playlist)

    val found = playlistRepository.findByPlaylistId(playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.numberOfTracks).isEqualTo(2)
  }

  @Test
  fun `numberOfTracks returns correct size after appendTracks`() {
    val playlistId = "playlist-append-${UUID.randomUUID()}"
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1")), albumId = AlbumId("al1"))),
      ),
    )

    playlistRepository.appendTracks(
      playlistId,
      listOf(
        PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
        PlaylistTrack(trackId = TrackId("t3"), artistIds = listOf(ArtistId("a3")), albumId = AlbumId("al3")),
      ),
    )

    val found = playlistRepository.findByPlaylistId(playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.numberOfTracks).isEqualTo(3)
  }

  @Test
  fun `save overwrites previous playlist data`() {
    val playlistId = "playlist-${UUID.randomUUID()}"
    playlistRepository.save(buildPlaylist(playlistId))

    playlistRepository.save(buildPlaylist(playlistId))

    val found = playlistRepository.findByPlaylistId(playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.tracks).hasSize(1)
  }

  @Test
  fun `findTrackCounts returns track count per playlist`() {
    val playlistId1 = "playlist-${UUID.randomUUID()}"
    val playlistId2 = "playlist-${UUID.randomUUID()}"
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId1,
        tracks = listOf(
          PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1")), albumId = AlbumId("al1")),
          PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
        ),
      ),
    )
    playlistRepository.save(buildPlaylist(playlistId2))

    val result = playlistRepository.findTrackCounts()

    assertThat(result).containsEntry(playlistId1, 2)
    assertThat(result).containsEntry(playlistId2, 1)
  }

  @Test
  fun `findDistinctArtistIds returns distinct artist ids per playlist`() {
    val playlistId1 = "playlist-${UUID.randomUUID()}"
    val playlistId2 = "playlist-${UUID.randomUUID()}"
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId1,
        tracks = listOf(
          PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1"), ArtistId("a2")), albumId = AlbumId("al1")),
          PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
        ),
      ),
    )
    playlistRepository.save(buildPlaylist(playlistId2))

    val result = playlistRepository.findDistinctArtistIds()

    assertThat(result[playlistId1]).containsExactlyInAnyOrder(ArtistId("a1"), ArtistId("a2"))
    assertThat(result[playlistId2]).containsExactlyInAnyOrder(ArtistId("artist-1"))
  }

  @Test
  fun `findDistinctArtistIds only considers each track's main artist, ignoring featured artists`() {
    val playlistId = "playlist-${UUID.randomUUID()}"
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(
          PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("main-1"), ArtistId("featured-1")), albumId = AlbumId("al1")),
        ),
      ),
    )

    val result = playlistRepository.findDistinctArtistIds()

    assertThat(result[playlistId]).containsExactly(ArtistId("main-1"))
  }

  @Test
  fun `findDistinctArtistIds and findTrackCounts do not fail for a document with a missing tracks field`() {
    val playlistId = "playlist-${UUID.randomUUID()}"
    val rawCollection = playlistDocumentRepository.mongoCollection().withDocumentClass(Document::class.java)
    rawCollection.insertOne(Document("_id", playlistId).append("spotifyPlaylistId", playlistId))
    try {
      assertThat(playlistRepository.findDistinctArtistIds()).containsEntry(playlistId, emptySet())
      assertThat(playlistRepository.findTrackCounts()).containsEntry(playlistId, 0)
    } finally {
      rawCollection.deleteOne(Filters.eq("_id", playlistId))
    }
  }

  @Test
  fun `findDistinctArtistIds includes playlists with no artists instead of omitting them`() {
    val playlistIdNoTracks = "playlist-${UUID.randomUUID()}"
    val playlistIdEmptyArtistIds = "playlist-${UUID.randomUUID()}"
    playlistRepository.save(Playlist(spotifyPlaylistId = playlistIdNoTracks, tracks = emptyList()))
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistIdEmptyArtistIds,
        tracks = listOf(PlaylistTrack(trackId = TrackId("t1"), artistIds = emptyList(), albumId = AlbumId("al1"))),
      ),
    )

    val result = playlistRepository.findDistinctArtistIds()

    assertThat(result).containsEntry(playlistIdNoTracks, emptySet())
    assertThat(result).containsEntry(playlistIdEmptyArtistIds, emptySet())
  }
}
