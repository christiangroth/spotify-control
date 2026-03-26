package de.chrgroth.spotify.control.domain.model.playlist

import de.chrgroth.spotify.control.domain.util.formatted

data class PlaylistCheckStats(
    val succeededChecks: Long,
    val totalChecks: Long,
    val allSucceeded: Boolean,
) {
    val succeededChecksFormatted: String get() = succeededChecks.formatted()
    val totalChecksFormatted: String get() = totalChecks.formatted()
}
