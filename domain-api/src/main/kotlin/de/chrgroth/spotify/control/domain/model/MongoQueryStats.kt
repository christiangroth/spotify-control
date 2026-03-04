package de.chrgroth.spotify.control.domain.model

data class MongoQueryStats(
    val name: String,
    val executionCountLast24h: Long,
    val slowQueryCount: Long,
)
