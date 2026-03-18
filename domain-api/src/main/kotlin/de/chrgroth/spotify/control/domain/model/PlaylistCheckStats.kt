package de.chrgroth.spotify.control.domain.model

import java.util.Locale

data class PlaylistCheckStats(
    val succeededChecks: Long,
    val totalChecks: Long,
    val allSucceeded: Boolean,
) {
    val succeededChecksFormatted: String get() = succeededChecks.formatted()
    val totalChecksFormatted: String get() = totalChecks.formatted()
}

private fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
