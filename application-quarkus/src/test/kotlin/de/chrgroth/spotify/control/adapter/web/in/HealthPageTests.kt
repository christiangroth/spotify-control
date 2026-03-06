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
  fun `health page outbox table contains blocked until column`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="outbox-table""""))
      .body(containsString("Blocked Until"))
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
  fun `health page contains grafana logs link in navbar`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="grafana-logs-link""""))
      .body(containsString("https://spotifycontrolprod.grafana.net/d/sadlil-loki-apps-dashboard/quarkus-logs"))
  }

  @Test
  fun `health page contains mongodb atlas link in navbar`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="mongodb-atlas-link""""))
      .body(containsString("https://cloud.mongodb.com/v2/699f08e3a7adcacf36dd2d4a#/explorer"))
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
  fun `health page contains outbox blocked-until countdown javascript`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("formatBlockedUntil"))
      .body(containsString("updateOutboxBlockedUntilCountdowns"))
      .body(containsString("startOutboxBlockedUntilInterval"))
      .body(containsString("outboxBlockedUntilInterval"))
      .body(containsString("data-blocked-until"))
      .body(containsString("outbox-blocked-until"))
  }

  @Test
  fun `health page outbox blocked-until uses dd-MM-yyyy HH-mm format for dates beyond 24h`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("TWENTY_FOUR_HOURS_MS"))
      .body(containsString("day + '.' + month + '.' + year + ' ' + hours + ':' + minutes"))
  }

  @Test
  fun `health page outbox blocked-until shows countdown in braces when less than 24h away`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("formatCountdown(remaining)"))
      .body(containsString("formatBlockedUntil(blockedUntil) + ' (' + formatCountdown(remaining) + ')'"))
  }

  @Test
  fun `health page outbox blocked-until interval is started and managed with 500ms`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("setInterval(updateOutboxBlockedUntilCountdowns, 500)"))
      .body(containsString("clearInterval(outboxBlockedUntilInterval)"))
  }

  @Test
  fun `health page outbox interval is cleared before sse snippet replacement`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("startOutboxBlockedUntilInterval"))
      .body(containsString("clearInterval"))
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
      .body(containsString("Size (kb)"))
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
  fun `health page lists all scheduled jobs in cronjob table`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("PlaylistSyncJob"))
      .body(containsString("RecentlyPlayedFetchJob"))
      .body(containsString("UserProfileUpdateJob"))
      .body(containsString("OutboxArchiveCleanupJob"))
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
      .body(containsString("sortCronjobTable"))
      .body(containsString("data-next-execution"))
      .body(containsString("cronjob-countdown"))
      .body(containsString("cronjob-pulse"))
      .body(containsString("cronjob-pulse-green"))
  }

  @Test
  fun `health snippet endpoint for cronjobs is available`() {
    given()
      .`when`()
      .get("/ui/health/snippets/cronjobs")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Scheduled Jobs"))
      .body(containsString("""data-testid="cronjobs-table""""))
  }

  @Test
  fun `health page cronjob sort only reorders dom when order changes`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("hasChanged"))
      .body(containsString("newOrder.forEach"))
  }

  @Test
  fun `health page cronjob pulse animation refreshes table from server after completion`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("fadeUpdate('snippet-cronjobs', '/ui/health/snippets/cronjobs'"))
  }

  @Test
  fun `health page cronjob sort excludes pulsing rows from reordering`() {
    given()
      .`when`()
      .get("/ui/health")
      .then()
      .statusCode(200)
      .body(containsString("pulsingRows"))
      .body(containsString("sortableRows"))
      .body(containsString("pulsingRows.concat(sortableRows)"))
  }
}
