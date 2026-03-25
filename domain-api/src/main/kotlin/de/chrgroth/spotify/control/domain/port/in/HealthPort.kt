package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.HealthStats

interface HealthPort {
    fun getStats(): HealthStats
}
