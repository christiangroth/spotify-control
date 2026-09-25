package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Clock

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SingularityArtistsPageTests {

  @Inject
  lateinit var appArtistRepository: AppArtistRepositoryPort

  @Test
  fun `singularity artists page is available`() {
    given()
      .`when`()
      .get("/playlists/singularity-artists")
      .then()
      .statusCode(200)
  }

  @Test
  fun `include endpoint returns not found for unknown artist`() {
    given()
      .`when`()
      .post("/playlists/singularity-artists/unknown-artist/include")
      .then()
      .statusCode(404)
  }

  @Test
  fun `exclude endpoint returns not found for unknown artist`() {
    given()
      .`when`()
      .post("/playlists/singularity-artists/unknown-artist/exclude")
      .then()
      .statusCode(404)
  }

  @Test
  fun `singularity artists page only lists review-pending artists`() {
    val pendingId = "singularity-page-pending-${UUID.randomUUID()}"
    val reviewedId = "singularity-page-reviewed-${UUID.randomUUID()}"
    appArtistRepository.upsertAll(
      listOf(
        AppArtist(id = ArtistId(pendingId), artistName = "Pending McPendington", lastSync = Clock.System.now(), singularityReviewPending = true),
        AppArtist(id = ArtistId(reviewedId), artistName = "Already Reviewed Artist", lastSync = Clock.System.now(), singularityReviewPending = false),
      ),
    )

    given()
      .`when`()
      .get("/playlists/singularity-artists")
      .then()
      .statusCode(200)
      .body(containsString("Pending McPendington"))
      .body(not(containsString("Already Reviewed Artist")))
  }

  @Test
  fun `confirming include clears review-pending and removes the artist from the page`() {
    val artistId = "singularity-page-confirm-include-${UUID.randomUUID()}"
    appArtistRepository.upsertAll(
      listOf(AppArtist(id = ArtistId(artistId), artistName = "Confirm Include Artist", lastSync = Clock.System.now(), singularityReviewPending = true)),
    )

    given()
      .`when`()
      .post("/playlists/singularity-artists/$artistId/include")
      .then()
      .statusCode(200)

    val result = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).first()
    assertThat(result.singularityIncluded).isTrue()
    assertThat(result.singularityReviewPending).isFalse()

    given()
      .`when`()
      .get("/playlists/singularity-artists")
      .then()
      .statusCode(200)
      .body(not(containsString("Confirm Include Artist")))
  }

  @Test
  fun `confirming exclude clears review-pending and removes the artist from the page`() {
    val artistId = "singularity-page-confirm-exclude-${UUID.randomUUID()}"
    appArtistRepository.upsertAll(
      listOf(AppArtist(id = ArtistId(artistId), artistName = "Confirm Exclude Artist", lastSync = Clock.System.now(), singularityReviewPending = true)),
    )

    given()
      .`when`()
      .post("/playlists/singularity-artists/$artistId/exclude")
      .then()
      .statusCode(200)

    val result = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).first()
    assertThat(result.singularityIncluded).isFalse()
    assertThat(result.singularityReviewPending).isFalse()

    given()
      .`when`()
      .get("/playlists/singularity-artists")
      .then()
      .statusCode(200)
      .body(not(containsString("Confirm Exclude Artist")))
  }
}
