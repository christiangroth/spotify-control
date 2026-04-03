package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigrateCurrentlyPlayingStartTimeStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigrateCurrentlyPlayingStartTimeStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(START_TIME_FIELD, Document("\$subtract", listOf("\$$OBSERVED_AT_FIELD", "\$$PROGRESS_MS_FIELD"))),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${result.modifiedCount} currently playing documents" }
  }

  companion object : KLogging() {
    private const val COLLECTION = "spotify_currently_playing"
    private const val START_TIME_FIELD = "startTime"
    private const val OBSERVED_AT_FIELD = "observedAt"
    private const val PROGRESS_MS_FIELD = "progressMs"
  }
}
