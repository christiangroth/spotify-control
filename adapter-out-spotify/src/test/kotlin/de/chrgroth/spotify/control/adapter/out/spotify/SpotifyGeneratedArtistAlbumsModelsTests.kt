package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingArtistDiscographyAlbumObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyGeneratedArtistAlbumsModelsTests {

  @Test
  fun `deserializes artist albums page generated model`() {
    val payload = """
      {
        "href": "https://api.spotify.com/v1/artists/artist-1/albums?offset=0&limit=20",
        "limit": 20,
        "items": [
          {
            "album_type": "album",
            "total_tracks": 10,
            "available_markets": [],
            "external_urls": {
              "spotify": "https://open.spotify.com/album/album-1"
            },
            "href": "https://api.spotify.com/v1/albums/album-1",
            "id": "album-1",
            "images": [],
            "name": "Album 1",
            "release_date": "2020-01-01",
            "release_date_precision": "day",
            "type": "album",
            "uri": "spotify:album:album-1",
            "artists": [],
            "album_group": "album"
          }
        ],
        "offset": 0,
        "previous": null,
        "total": 1,
        "next": "https://api.spotify.com/v1/artists/artist-1/albums?offset=20&limit=20"
      }
    """.trimIndent()

    val parsed = spotifyJson.decodeFromString<PagingArtistDiscographyAlbumObject>(payload)

    assertThat(parsed.items).hasSize(1)
    assertThat(parsed.items.first().id).isEqualTo("album-1")
    assertThat(parsed.next).contains("offset=20")
  }
}
