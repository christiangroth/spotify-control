package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoCollectionStatsPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

// caches the result of the (potentially slow) collStats calls so that every reader – HealthService's health page/SSE
// and MongoCollectionMetrics' gauges alike – shares a single refresh instead of each querying MongoDB independently,
// following the same "a single refresh serves every reader" pattern as OutboxPartitionStatsCache. current() lazily
// performs the first fetch on-demand so collection names/sizes are already known by the time MongoCollectionMetrics
// registers its gauges on StartupEvent, without depending on CDI startup-observer ordering between beans.
@ApplicationScoped
@Suppress("Unused")
class MongoStatsAdapter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : MongoCollectionStatsPort {

  @Volatile
  private var cachedStats: List<MongoCollectionStats>? = null

  override fun current(): List<MongoCollectionStats> = cachedStats ?: refresh()

  // Collection sizes change slowly, and on our Atlas tier each collStats takes ~0.7s (one per collection per refresh), so
  // refreshing every 15s (later 5m) made collStats the single largest consumer of MongoDB time.
  @Scheduled(every = "30m", delayed = "12s")
  fun scheduledRefresh() {
    refresh()
  }

  fun refresh(): List<MongoCollectionStats> {
    val database = mongoClient.getDatabase(databaseName)
    return try {
      val stats = mongoQueryMetrics.timed("mongodb.listCollections") { database.listCollectionNames().toList() }
        .sorted()
        .mapNotNull { collectionName ->
          try {
            val collStats = mongoQueryMetrics.timed("mongodb.collStats") { database.runCommand(Document("collStats", collectionName)) }
            MongoCollectionStats(
              name = collectionName,
              documentCount = (collStats["count"] as? Number)?.toLong() ?: 0L,
              size = (collStats["size"] as? Number)?.toLong() ?: 0L,
            )
          } catch (e: MongoException) {
            logger.warn(e) { "Failed to get stats for collection '$collectionName'" }
            null
          }
        }
      cachedStats = stats
      stats
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to refresh MongoDB collection stats, keeping previous values" }
      cachedStats ?: emptyList()
    }
  }

  companion object : KLogging()
}
