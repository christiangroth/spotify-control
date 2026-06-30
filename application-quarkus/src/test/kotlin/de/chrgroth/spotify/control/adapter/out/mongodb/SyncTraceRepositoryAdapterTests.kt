package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class SyncTraceRepositoryAdapterTests {

  @Inject
  lateinit var syncTraceRepository: SyncTraceRepositoryPort

  private val triggeredAt = Instant.fromEpochSeconds(123)

  @Test
  fun `find returns null when no trace recorded`() {
    val result = syncTraceRepository.find(SyncTraceEntityType.ARTIST, "unknown-${UUID.randomUUID()}")

    assertThat(result).isNull()
  }

  @Test
  fun `upsert and find round-trips a playback cause`() {
    val artistId = "artist-playback-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.Playback("track-1"), triggeredAt))

    val result = syncTraceRepository.find(SyncTraceEntityType.ARTIST, artistId)

    assertThat(result).isEqualTo(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.Playback("track-1"), triggeredAt))
  }

  @Test
  fun `upsert and find round-trips a playlist cause`() {
    val artistId = "artist-playlist-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.Playlist("playlist-1", "track-1"), triggeredAt))

    val result = syncTraceRepository.find(SyncTraceEntityType.ARTIST, artistId)

    assertThat(result).isEqualTo(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.Playlist("playlist-1", "track-1"), triggeredAt))
  }

  @Test
  fun `upsert and find round-trips an artist discography cause for an album`() {
    val albumId = "album-discography-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ALBUM, albumId, SyncCause.ArtistDiscography("artist-1"), triggeredAt))

    val result = syncTraceRepository.find(SyncTraceEntityType.ALBUM, albumId)

    assertThat(result).isEqualTo(SyncTrace(SyncTraceEntityType.ALBUM, albumId, SyncCause.ArtistDiscography("artist-1"), triggeredAt))
  }

  @Test
  fun `upsert and find round-trips a manual resync cause`() {
    val artistId = "artist-manual-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.ManualResync, triggeredAt))

    val result = syncTraceRepository.find(SyncTraceEntityType.ARTIST, artistId)

    assertThat(result).isEqualTo(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.ManualResync, triggeredAt))
  }

  @Test
  fun `upsert overwrites the cause on re-upsert for the same entity`() {
    val artistId = "artist-overwrite-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.Playback("track-1"), triggeredAt))

    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, SyncCause.ManualResync, triggeredAt))

    val result = syncTraceRepository.find(SyncTraceEntityType.ARTIST, artistId)
    assertThat(result?.cause).isEqualTo(SyncCause.ManualResync)
  }

  @Test
  fun `artist and album traces with the same entityId are stored independently`() {
    val sharedId = "shared-id-${UUID.randomUUID()}"
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, sharedId, SyncCause.Playback("track-1"), triggeredAt))
    syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ALBUM, sharedId, SyncCause.ManualResync, triggeredAt))

    val artistResult = syncTraceRepository.find(SyncTraceEntityType.ARTIST, sharedId)
    val albumResult = syncTraceRepository.find(SyncTraceEntityType.ALBUM, sharedId)

    assertThat(artistResult?.cause).isEqualTo(SyncCause.Playback("track-1"))
    assertThat(albumResult?.cause).isEqualTo(SyncCause.ManualResync)
  }
}
