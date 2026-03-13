package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.ConfigurationStats

interface ConfigurationInfoPort {
    fun getConfigurationStats(): ConfigurationStats
}
