package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class HealthPageTests {

  @Test
  fun `health page is available and displays system health heading`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("System Health"))
  }

  @Test
  fun `health page displays health section with communication, cronjobs and mongodb sub-headings`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("""id="health-section""""))
      .body(containsString("Communication"))
      .body(containsString("Cronjobs"))
      .body(containsString("MongoDB"))
      .body(containsString("Outgoing HTTP Requests"))
      .body(containsString("Outbox Partitions"))
      .body(containsString("Collections"))
      .body(containsString("Queries (Last 24h)"))
  }

  @Test
  fun `health page contains sse connection setup with reconnect interval`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("EventSource"))
      .body(containsString("/ui/health/events"))
      .body(containsString("setInterval"))
      .body(containsString("60000"))
  }

  @Test
  fun `health page uses specific sse events with fade updates instead of full reload`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("refresh-outgoing-http-calls"))
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("fadeUpdate"))
  }

  @Test
  fun `health page contains health link in navbar`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="health-link""""))
  }

  @Test
  fun `health snippet endpoint for outgoing http calls is available`() {
    given()
      .`when`()
      .get("/ui/health/snippets/outgoing-http-calls")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Outgoing HTTP Requests"))
  }

  @Test
  fun `health snippet endpoint for outbox partitions is available`() {
    given()
      .`when`()
      .get("/ui/health/snippets/outbox-partitions")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Outbox Partitions"))
  }

  @Test
  fun `health snippet endpoint for mongodb collections is available`() {
    given()
      .`when`()
      .get("/ui/health/snippets/mongodb-collections")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Collections"))
      .body(containsString("""data-testid="mongodb-collections-table""""))
  }

  @Test
  fun `health snippet endpoint for mongodb queries is available`() {
    given()
      .`when`()
      .get("/ui/health/snippets/mongodb-queries")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Queries (Last 24h)"))
      .body(containsString("""data-testid="mongodb-queries-table""""))
  }

  @Test
  fun `health page displays cronjobs section with table`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("Cronjobs"))
      .body(containsString("Scheduled Jobs"))
      .body(containsString("""data-testid="cronjobs-table""""))
      .body(containsString("Cron Schedule"))
      .body(containsString("Status"))
      .body(containsString("Next Execution"))
  }

  @Test
  fun `health page contains cronjob countdown javascript`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("formatCountdown"))
      .body(containsString("updateCronjobCountdowns"))
      .body(containsString("data-next-execution"))
      .body(containsString("cronjob-countdown"))
      .body(containsString("cronjob-pulse"))
      .body(containsString("cronjob-pulse-green"))
  }
}
