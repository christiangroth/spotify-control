package de.chrgroth.spotify.control.domain.model

data class MongoCollectionStats(
    val name: String,
    val documentCount: Long,
    val size: Long,
)
