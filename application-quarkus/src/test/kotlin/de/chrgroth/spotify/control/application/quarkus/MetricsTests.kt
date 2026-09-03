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

    // Groups such as scheduler_*/worker_* only appear once a @Scheduled job has actually run on the Vert.x worker pool, which is timing-dependent
    // depending on the app instance (shared vs. isolated IT). Assert the always-present core groups instead of exact list equality, so additional
    // timing-dependent groups don't make this test flaky.
    val topLevelGroups = metrics.lines()
      .filter { it.isNotBlank() }
      .filterNot { it.startsWith("#") }
      .map { it.split("_")[0] }
      .distinct()
      .sorted()
    assertThat(topLevelGroups).contains("app", "application", "http", "jvm", "mongodb", "netty", "outbox", "process", "spotify", "system")
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

  @Test
  fun `check mongodb collection size gauges are exposed`() {
    val metrics = given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    assertThat(metrics).contains("mongodb_collection_size_bytes")
  }
}
