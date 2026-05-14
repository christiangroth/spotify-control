package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingArtistDiscographyAlbumObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyGeneratedArtistAlbumsModelsTests {

  @Test
  fun `deserializes artist albums page with partial album payload`() {
    val payload = """
      {
        "items": [
          {
            "id": "album-1"
          },
          {
            "name": "Name only"
          }
        ],
        "next": "https://api.spotify.com/v1/artists/artist-1/albums?offset=20&limit=20"
      }
    """.trimIndent()

    val parsed = spotifyJson.decodeFromString<PagingArtistDiscographyAlbumObject>(payload)
    val items = parsed.items ?: emptyList()

    assertThat(items).hasSize(2)
    assertThat(items.first().id).isEqualTo("album-1")
    assertThat(items[1].id).isNull()
    assertThat(parsed.next).contains("offset=20")
  }
}
