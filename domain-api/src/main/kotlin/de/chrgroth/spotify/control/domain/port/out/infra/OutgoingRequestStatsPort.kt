package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.OutgoingRequestStats

interface OutgoingRequestStatsPort {
    fun getRequestStats(): List<OutgoingRequestStats>
}
