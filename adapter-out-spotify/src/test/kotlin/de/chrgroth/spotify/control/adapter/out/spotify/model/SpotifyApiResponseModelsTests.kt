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

  @Test
  fun `deserializes several-artists response with real Spotify payload shape`() {
    val payload = """
      {
        "artists": [
          {
            "external_urls": { "spotify": "https://open.spotify.com/artist/0oSGxfWSnnOXhD2fKuz2Gy" },
            "followers": { "href": null, "total": 1000 },
            "genres": ["art rock", "glam rock"],
            "href": "https://api.spotify.com/v1/artists/0oSGxfWSnnOXhD2fKuz2Gy",
            "id": "0oSGxfWSnnOXhD2fKuz2Gy",
            "images": [],
            "name": "David Bowie",
            "popularity": 76,
            "type": "artist",
            "uri": "spotify:artist:0oSGxfWSnnOXhD2fKuz2Gy"
          },
          null
        ]
      }
    """.trimIndent()

    val result = json.decodeFromString<SeveralArtistsObject>(payload)

    assertThat(result.artists).hasSize(2)
    assertThat(result.artists[0]?.id).isEqualTo("0oSGxfWSnnOXhD2fKuz2Gy")
    assertThat(result.artists[0]?.name).isEqualTo("David Bowie")
    assertThat(result.artists[1]).isNull()
  }
}
