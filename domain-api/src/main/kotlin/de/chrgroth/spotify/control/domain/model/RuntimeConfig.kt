package de.chrgroth.spotify.control.domain.model

data class RuntimeConfig(
    val throttleIntervalMs: Long,
    val defaultThrottleIntervalMs: Long,
)
