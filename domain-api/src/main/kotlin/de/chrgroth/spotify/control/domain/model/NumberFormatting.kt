package de.chrgroth.spotify.control.domain.model

import java.util.Locale

fun Long.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
fun Int.formatted(): String = String.format(Locale.US, "%,d", this).replace(",", ".")
