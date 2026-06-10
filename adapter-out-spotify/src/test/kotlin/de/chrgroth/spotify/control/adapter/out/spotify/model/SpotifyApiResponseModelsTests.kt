package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyApiResponseModelsTests {

  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  @Test
  fun `deserializes album response with missing optional fields`() {
    val payload = """
      {
        "id": "album-1",
        "name": "Album One",
        "tracks": {
          "items": [
            {
              "id": "track-1",
              "name": "Track One",
              "artists": [
                { "id": "artist-1", "name": "Artist One" }
              ]
            }
          ]
        }
      }
    """.trimIndent()

    val album = json.decodeFromString<AlbumObject>(payload)

    assertThat(album.id).isEqualTo("album-1")
    assertThat(album.name).isEqualTo("Album One")
    assertThat(album.tracks.items).hasSize(1)
    assertThat(album.tracks.next).isNull()
    assertThat(album.releaseDatePrecision).isNull()
    assertThat(album.albumType).isNull()
  }
}
