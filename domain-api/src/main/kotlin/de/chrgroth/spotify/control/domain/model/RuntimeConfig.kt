package de.chrgroth.spotify.control.domain.model

data class RuntimeConfig(
    val throttleIntervalSeconds: Long,
    val defaultThrottleIntervalSeconds: Long,
)
