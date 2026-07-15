package de.chrgroth.spotify.control.domain.model.infra

data class HealthStats(
  val outgoingRequestStats: List<OutgoingRequestStats> = emptyList(),
  val outboxPartitions: List<OutboxPartitionStats> = emptyList(),
  val mongoCollectionStats: List<MongoCollectionStats> = emptyList(),
  val mongoQueryStats: List<MongoQueryStats> = emptyList(),
  val cronjobStats: List<CronjobStats> = emptyList(),
  val predicateStats: List<PredicateStats> = emptyList(),
  val configurationStats: ConfigurationStats = ConfigurationStats(emptyList(), emptyList()),
) {
  val mongoCollectionDocumentTotal: Long get() = mongoCollectionStats.sumOf { it.documentCount }
  val mongoCollectionSizeTotalKb: Long get() = mongoCollectionStats.sumOf { it.sizeKb }
  val outboxAllActive: Boolean get() = outboxPartitions.all { it.status == "ACTIVE" }
  val playbackActive: Boolean? get() = predicateStats.firstOrNull { it.name == "playbackActive" }?.active
}
