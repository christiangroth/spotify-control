package de.chrgroth.spotify.control.domain.model

import java.util.Locale

data class TopEntry(
    val name: String,
    val totalMinutes: Long,
    val imageUrl: String? = null,
) {
    val totalMinutesFormatted: String get() = totalMinutes.formatted()
}

private fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
