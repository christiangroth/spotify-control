package de.chrgroth.spotify.control.domain.model

data class HealthStats(
    val outgoingRequestStats: List<OutgoingRequestStats>,
    val outboxPartitions: List<OutboxPartitionStats>,
    val mongoCollectionStats: List<MongoCollectionStats>,
    val mongoQueryStats: List<MongoQueryStats>,
    val cronjobStats: List<CronjobStats>,
) {
    val mongoCollectionDocumentTotal: Long get() = mongoCollectionStats.sumOf { it.documentCount }
    val mongoCollectionSizeTotal: Long get() = mongoCollectionStats.sumOf { it.size }
}
