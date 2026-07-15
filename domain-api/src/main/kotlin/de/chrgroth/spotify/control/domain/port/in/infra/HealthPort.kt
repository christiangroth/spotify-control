package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.infra.ConfigurationStats
import de.chrgroth.spotify.control.domain.model.infra.CronjobStats
import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.model.infra.PredicateStats

interface HealthPort {
  fun getStats(): HealthStats
  fun getOutboxPartitions(): List<OutboxPartitionStats>
  fun getOutgoingRequestStats(): List<OutgoingRequestStats>
  fun getMongoCollectionStats(): List<MongoCollectionStats>
  fun getMongoQueryStats(): List<MongoQueryStats>
  fun getCronjobStats(): List<CronjobStats>
  fun getPredicateStats(): List<PredicateStats>
  fun getConfigurationStats(): ConfigurationStats
}
