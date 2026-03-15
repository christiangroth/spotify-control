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
        throttleIntervalSeconds = spotifyThrottling.getThrottleIntervalMs() / MILLIS_PER_SECOND,
        defaultThrottleIntervalSeconds = spotifyThrottling.getDefaultThrottleIntervalMs() / MILLIS_PER_SECOND,
    )

    override fun setThrottleIntervalSeconds(seconds: Long) {
        spotifyThrottling.setThrottleIntervalMs(seconds * MILLIS_PER_SECOND)
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
