package de.chrgroth.spotify.control.domain.model

import java.time.Instant

data class PredicateStats(
    val name: String,
    val active: Boolean,
    val lastCheck: Instant? = null,
)
