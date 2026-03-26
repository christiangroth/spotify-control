package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.HealthStats

interface HealthPort {
    fun getStats(): HealthStats
}
