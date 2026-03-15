package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.RuntimeConfig
import de.chrgroth.spotify.control.domain.port.`in`.RuntimeConfigPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyThrottlingPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class RuntimeConfigAdapter(
    private val spotifyThrottling: SpotifyThrottlingPort,
) : RuntimeConfigPort {

    override fun getRuntimeConfig(): RuntimeConfig = RuntimeConfig(
        throttleIntervalMs = spotifyThrottling.getThrottleIntervalMs(),
        defaultThrottleIntervalMs = spotifyThrottling.getDefaultThrottleIntervalMs(),
    )

    override fun setThrottleIntervalMs(ms: Long) {
        spotifyThrottling.setThrottleIntervalMs(ms)
    }
}
