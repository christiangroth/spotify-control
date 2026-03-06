package de.chrgroth.spotify.control.domain.model

import java.time.Instant

data class CronjobStats(
    val simpleName: String,
    val cronSchedule: String,
    val nextExecution: Instant?,
    val running: Boolean,
)
