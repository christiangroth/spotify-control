package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class OutboxViewerPageTests {

  @Test
  fun `outbox-viewer page is available and displays outbox heading`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Outbox"))
  }

  @Test
  fun `outbox-viewer page contains sse connection setup with reconnect interval`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .body(containsString("sse-utils.js"))
      .body(containsString("connectSse"))
      .body(containsString("/health/events"))
  }

  @Test
  fun `outbox-viewer page uses specific sse events with patch updates instead of full reload`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("patchUpdate"))
      .body(containsString("snippet-outbox-tasks"))
  }

  @Test
  fun `outbox-viewer page refreshes tasks snippet on sse open`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .body(containsString("connectSse('/health/events'"))
      .body(containsString("/outbox-viewer/snippets/tasks"))
  }

  @Test
  fun `outbox-viewer snippet endpoint for tasks is available`() {
    given()
      .`when`()
      .get("/outbox-viewer/snippets/tasks")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
  }

  @Test
  fun `outbox-viewer page navbar sse connection to health events refreshes widgets on open`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .body(containsString("connectSse('/health/events'"))
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("refresh-playback-state"))
  }

  @Test
  fun `outbox-viewer page contains wipe outbox button and confirmation modal`() {
    given()
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .body(containsString("Wipe Outbox"))
      .body(containsString("wipe-outbox-btn"))
      .body(containsString("/outbox-viewer/wipe"))
  }

  @Test
  fun `outbox-viewer wipe endpoint clears outbox and returns ok status`() {
    given()
      .`when`()
      .post("/outbox-viewer/wipe")
      .then()
      .statusCode(200)
      .body("status", equalTo("ok"))
  }
}
