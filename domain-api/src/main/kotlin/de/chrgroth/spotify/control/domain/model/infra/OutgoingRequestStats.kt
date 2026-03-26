package de.chrgroth.spotify.control.domain.model.infra

import de.chrgroth.spotify.control.domain.util.formatted

data class OutgoingRequestStats(
    val endpoint: String,
    val requestCountLast24h: Long,
) {
    val requestCountLast24hFormatted: String get() = requestCountLast24h.formatted()
}
