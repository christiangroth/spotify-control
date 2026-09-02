package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest

// Runs the MetricsTests suite against the packaged artifact (native executable when
// invoked via the `testNative` Gradle task), so missing metrics registration in the native build is caught in CI.
@QuarkusIntegrationTest
class MetricsIT : MetricsTests()
