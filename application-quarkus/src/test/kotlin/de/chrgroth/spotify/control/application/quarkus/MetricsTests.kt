package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class MetricsTests {

  @Test
  fun `check top-level metrics groups`() {
    val metrics = given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    // scheduler_* only appears once a @Scheduled job has actually run, which is timing-dependent in the shared test app instance
    val topLevelGroups = metrics.lines()
      .filter { it.isNotBlank() }
      .filterNot { it.startsWith("#") }
      .map { it.split("_")[0] }
      .distinct()
      .filterNot { it == "scheduler" }
      .sorted()
    assertThat(topLevelGroups).isEqualTo(listOf("app", "application", "http", "jvm", "mongodb", "netty", "outbox", "process", "spotify", "system", "worker"))
  }

  @Test
  fun `check overview dashboard gauges are exposed`() {
    val metrics = given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    assertThat(metrics).contains("app_users_active")
    assertThat(metrics).contains("app_playlist_tracked")
    assertThat(metrics).contains("app_playlist_album_upgrade_pending")
    assertThat(metrics).contains("app_playlist_sync_job_last_success_timestamp")
  }

  @Test
  fun `check domain metrics dashboard gauges are exposed`() {
    val metrics = given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    assertThat(metrics).contains("app_playlist_out_of_sync")
    assertThat(metrics).contains("app_catalog_artists")
    assertThat(metrics).contains("app_catalog_tracks")
    assertThat(metrics).contains("app_catalog_albums")
  }

  @Test
  fun `check outbox pending gauges are exposed`() {
    val metrics = given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    assertThat(metrics).contains("outbox_partition_pending")
    assertThat(metrics).contains("outbox_event_type_pending")
  }
}
