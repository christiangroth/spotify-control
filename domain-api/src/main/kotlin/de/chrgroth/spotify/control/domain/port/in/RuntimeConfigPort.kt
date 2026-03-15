package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.RuntimeConfig

interface RuntimeConfigPort {
    fun getRuntimeConfig(): RuntimeConfig
    fun setThrottleIntervalSeconds(seconds: Long)
}
