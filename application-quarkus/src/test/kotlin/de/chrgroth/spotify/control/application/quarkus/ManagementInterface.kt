package de.chrgroth.spotify.control.application.quarkus

import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.eclipse.microprofile.config.ConfigProvider

// Non-application endpoints (/q/health, /q/metrics, ...) are served on the Quarkus management interface, not on the
// normal HTTP port. Use this helper for every REST Assured call to /q/* (JVM and integration/native tests).
object ManagementInterface {

  private const val DEFAULT_TEST_PORT = 9001

  val testPort: Int
    get() = ConfigProvider.getConfig()
      .getOptionalValue("quarkus.management.test-port", Int::class.javaObjectType)
      .orElse(DEFAULT_TEST_PORT)

  fun given(): RequestSpecification = RestAssured.given().port(testPort)
}
