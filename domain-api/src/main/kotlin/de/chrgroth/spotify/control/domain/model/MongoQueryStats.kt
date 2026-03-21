package de.chrgroth.spotify.control.domain.model

/**
 * Stats for a tracked MongoDB query operation within a 24-hour sliding window.
 *
 * @property name the name of the tracked operation (e.g. "user.findById")
 * @property executionCountLast24h number of times this operation was executed in the last 24 hours
 * @property slowQueryCount number of executions in the last 24 hours that exceeded the configured
 *   slow-query threshold (see `app.mongodb.slow-query-threshold-ms`, default 250ms)
 * @property timeoutCount number of executions in the last 24 hours that exceeded the configured
 *   query timeout (see `app.mongodb.query-timeout-ms`, default 2000ms) and returned a fallback value
 */
data class MongoQueryStats(
    val name: String,
    val executionCountLast24h: Long,
    val slowQueryCount: Long,
    val timeoutCount: Long = 0L,
) {
    val executionCountLast24hFormatted: String get() = executionCountLast24h.formatted()
    val slowQueryCountFormatted: String get() = slowQueryCount.formatted()
    val timeoutCountFormatted: String get() = timeoutCount.formatted()
}
