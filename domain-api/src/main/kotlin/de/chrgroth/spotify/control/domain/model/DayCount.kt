package de.chrgroth.spotify.control.domain.model

import kotlinx.datetime.LocalDate
import java.util.Locale

data class DayCount(
    val date: LocalDate,
    val count: Long,
    val heightPercent: Int,
    val dateLabel: String,
) {
    val countFormatted: String get() = count.formatted()
}

private fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
