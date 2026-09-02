package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest

// Runs the HealthCheckTests suite against the packaged artifact (native executable when
// invoked via the `testNative` Gradle task), so a broken native build/startup is caught in CI.
@QuarkusIntegrationTest
class HealthCheckIT : HealthCheckTests()
