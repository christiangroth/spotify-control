package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test
import java.util.logging.Logger

@QuarkusTest
@TestSecurity(user = "test-user-a")
class LogsViewerPageTests {

  @Test
  fun `logs viewer renders a visible fallback time value`() {
    Logger.getLogger("de.chrgroth.spotify.control.TestLogsViewerPage").warning("logs-viewer-time-test")

    val body = given()
      .`when`()
      .get("/logs-viewer")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .extract()
      .body()
      .asString()

    assertThat(body).contains("logs-viewer-time-test")
    assertThat(Regex("""class="log-time" data-epoch-ms="\d+">\d{2}:\d{2}:\d{2}</span>""").containsMatchIn(body)).isTrue()
  }
}
