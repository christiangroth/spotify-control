package de.chrgroth.spotify.control.domain.model

data class MongoViewerFilter(
    val field: String,
    val operator: MongoViewerFilterOperator,
    val value: String,
)
