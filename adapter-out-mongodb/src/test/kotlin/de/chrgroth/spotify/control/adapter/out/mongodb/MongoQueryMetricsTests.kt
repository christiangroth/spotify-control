package de.chrgroth.spotify.control.adapter.out.mongodb

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MongoQueryMetricsTests {

    private val meterRegistry = SimpleMeterRegistry()
    private val metrics = MongoQueryMetrics(
        meterRegistry = meterRegistry,
        slowQueryThresholdMs = 100L,
        queryTimeoutMs = 200L,
    )

    @Test
    fun `timedWithFallback returns block result on success`() {
        val result = metrics.timedWithFallback("test.op", "fallback") { "actual" }
        assertThat(result).isEqualTo("actual")
    }

    @Test
    fun `timedWithFallback returns fallback on timeout`() {
        val result = metrics.timedWithFallback("test.timeout", "fallback") {
            Thread.sleep(500)
            "actual"
        }
        assertThat(result).isEqualTo("fallback")
    }

    @Test
    fun `timedWithFallback records timeout in getQueryStats`() {
        metrics.timedWithFallback("test.timeout.stats", emptyList<String>()) {
            Thread.sleep(500)
            listOf("a", "b")
        }

        val stats = metrics.getQueryStats()
        val stat = stats.find { it.name == "test.timeout.stats" }
        assertThat(stat).isNotNull()
        assertThat(stat!!.timeoutCount).isEqualTo(1L)
        assertThat(stat.executionCountLast24h).isEqualTo(0L)
    }

    @Test
    fun `timedWithFallback records execution count on success`() {
        metrics.timedWithFallback("test.success.stats", 0L) { 42L }

        val stats = metrics.getQueryStats()
        val stat = stats.find { it.name == "test.success.stats" }
        assertThat(stat).isNotNull()
        assertThat(stat!!.executionCountLast24h).isEqualTo(1L)
        assertThat(stat.timeoutCount).isEqualTo(0L)
    }

    @Test
    fun `timedWithFallback records timeout counter in micrometer`() {
        metrics.timedWithFallback("test.meter.timeout", Unit) {
            Thread.sleep(500)
        }

        val counter = meterRegistry.find("mongodb.timeout.queries")
            .tag("operation", "test.meter.timeout")
            .counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
